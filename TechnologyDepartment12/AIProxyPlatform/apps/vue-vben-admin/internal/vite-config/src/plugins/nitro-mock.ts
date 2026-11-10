import type { PluginOption } from 'vite';

import type { NitroMockPluginOptions } from '../typing';

import { isAbsolute, join, resolve } from 'node:path';

import {
  colors,
  consola,
  findMonorepoRoot,
  getPackage,
} from '@vben/node-utils';

import getPort from 'get-port';
import { build, createDevServer, createNitro, prepare } from 'nitropack';

const hmrKeyRe = /^runtimeConfig\.|routeRules\./;

/**
 * 将 $/xxx 形式的路径解析为 <外层 monorepo 根>/xxx
 * 例如 findMonorepoRoot() 指向 vue-vben-admin 时，$/apps/backend-mock-template
 * 会解析到 <外层 monorepo 根>/apps/backend-mock-template
 */
function resolveDollarPath(p: string): string {
  const outerRoot = join(findMonorepoRoot(), '..', '..');
  return resolve(outerRoot, p.slice(2));
}

export const viteNitroMockPlugin = ({
  mockServerPackage = '$/apps/backend-mock-template',
  port = 5320,
  verbose = true,
}: NitroMockPluginOptions = {}): PluginOption => {
  return {
    async configureServer(server) {
      const availablePort = await getPort({ port });
      if (availablePort !== port) {
        return;
      }

      // mockServerPackage 支持三种形式：
      //   - 绝对/相对路径（./ ../ /abs）：原样 resolve
      //   - $/xxx：解析为 <外层 monorepo 根>/apps/xxx
      //   - 其它：当作包名走 workspace 查找
      let rootDir: string;
      if (
        isAbsolute(mockServerPackage) ||
        mockServerPackage.startsWith('./') ||
        mockServerPackage.startsWith('../')
      ) {
        rootDir = resolve(mockServerPackage);
      } else if (mockServerPackage.startsWith('$/')) {
        rootDir = resolveDollarPath(mockServerPackage);
      } else {
        const pkg = await getPackage(mockServerPackage);
        if (!pkg) {
          consola.log(
            `Package ${mockServerPackage} not found. Skip mock server.`,
          );
          return;
        }
        rootDir = pkg.dir;
      }

      runNitroServer(rootDir, port, verbose);

      const _printUrls = server.printUrls;
      server.printUrls = () => {
        _printUrls();

        consola.log(
          `  ${colors.green('➜')}  ${colors.bold('Nitro Mock Server')}: ${colors.cyan(`http://localhost:${port}/api`)}`,
        );
      };
    },
    enforce: 'pre',
    name: 'vite:mock-server',
  };
};

async function runNitroServer(rootDir: string, port: number, verbose: boolean) {
  let nitro: any;
  const reload = async () => {
    if (nitro) {
      consola.info('Restarting dev server...');
      if ('unwatch' in nitro.options._c12) {
        await nitro.options._c12.unwatch();
      }
      await nitro.close();
    }
    nitro = await createNitro(
      {
        dev: true,
        preset: 'nitro-dev',
        rootDir,
      },
      {
        c12: {
          async onUpdate({ getDiff, newConfig }) {
            const diff = getDiff();
            if (diff.length === 0) {
              return;
            }
            verbose &&
              consola.info(
                `Nitro config updated:\n${diff
                  .map((entry) => `  ${entry.toString()}`)
                  .join('\n')}`,
              );
            await (diff.every((e) => hmrKeyRe.test(e.key))
              ? nitro.updateConfig(newConfig.config)
              : reload());
          },
        },
        watch: true,
      },
    );
    nitro.hooks.hookOnce('restart', reload);

    const server = createDevServer(nitro);
    await server.listen(port, { showURL: false });
    await prepare(nitro);
    await build(nitro);

    if (verbose) {
      console.log('');
      consola.success(colors.bold(colors.green('Nitro Mock Server started.')));
    }
  };
  return await reload();
}
