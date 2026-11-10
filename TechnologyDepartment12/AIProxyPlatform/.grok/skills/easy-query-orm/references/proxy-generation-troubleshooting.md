# Proxy Generation Troubleshooting

Use this first when the symptom is one of:

- `cannot find symbol: UserProxy`
- `package ...proxy does not exist`
- `*Proxy` not generated
- Java APT did not run
- Kotlin KSP output missing
- IDE shows proxy unresolved after build
- `annotationProcessorPaths` / processor order suspicion
- `javacTree`, Lombok, MapStruct, or JDK annotation-processor compatibility errors

This file is for diagnosis order, not full setup. After identifying the failing
layer, jump to `setup-java.md` or `setup-kotlin.md`.

## 1. Identify the generation mode first

- Java project: easy-query proxy generation uses JSR-269 APT through
  `sql-processor`.
- Kotlin project: use KSP through `sql-ksp-processor`.
- Do not treat KAPT as the normal Kotlin path.
- `@EntityProxy` is the generation trigger.
- `@EntityFileProxy` is a different path and should be treated as plugin/file
  generation, not standard APT/KSP generation.

If the project uses `@EntityFileProxy`, do not debug it as a normal Maven APT
 failure.

## 2. Confirm the processor is actually wired

### Java / Maven

Check one of these is true:

- the module directly depends on `com.easy-query:sql-processor`, or
- `maven-compiler-plugin` includes `sql-processor` in
  `annotationProcessorPaths`.

Critical rule:

- if the project already configures `annotationProcessorPaths`, adding
  `sql-processor` only as a normal dependency may not be enough; put it in the
  processor path explicitly.

Also check:

- `maven-compiler-plugin` does not set `<proc>none</proc>`
- the module that contains the `@EntityProxy` entity is the module that wires
  the processor

### Kotlin / Gradle

Check all of these:

- plugin `com.google.devtools.ksp` is applied
- dependency uses `ksp("com.easy-query:sql-ksp-processor:...")`
- not `kapt("com.easy-query:...")`

## 3. Check the trigger class, not only the import error

For a target entity / DTO / VO:

- it has `@EntityProxy`
- it is in the module currently being compiled
- it is not accidentally switched to `@EntityFileProxy`

Do not claim proxy generation depends on `ProxyEntityAvailable`. That interface
is a common typed-usage style, but the generation trigger is `@EntityProxy`.

## 4. Check where generated files should appear

### Java

Expected default output:

- Maven CLI / javac APT: usually under `target/generated-sources/annotations`

If files exist there but the IDE still reports unresolved proxy types:

- refresh Maven/Gradle import
- mark generated sources as generated/source root if the IDE did not pick it up
- verify the generated package matches the import

### Kotlin

Expected default output:

- `build/generated/ksp/main/kotlin`

If CLI build works but IDE still reports unresolved proxy types:

- add the generated KSP directory to the Kotlin source set
- refresh Gradle import

## 5. Treat `*Proxy` missing as a secondary symptom until proven otherwise

This is the most important diagnostic rule.

If the compiler or another processor fails before easy-query completes, the
visible error often becomes:

- `package xxx.proxy does not exist`
- `cannot find symbol: XxxProxy`

But the real first error may be elsewhere:

- Java syntax error
- another module not compiling
- Lombok/JDK incompatibility
- MapStruct processor crash
- mixed processor ordering issue

So always inspect the first real compile error in the build log, not only the
last proxy import error.

## 6. Known high-probability failure classes

### Processor chain crash

Typical signs:

- `javacTree`
- `NoSuchFieldError`
- `JCImport`
- compiler internal API errors

Likely cause:

- Lombok or another processor is too old for the current JDK / javac

First fix:

- upgrade Lombok first
- then re-run build and inspect the first error again

### Existing `annotationProcessorPaths` hides easy-query

Typical sign:

- project declares `sql-processor`, but no proxy files are emitted

Cause:

- `maven-compiler-plugin` uses explicit `annotationProcessorPaths`, and
  easy-query is missing from that list

First fix:

- add `sql-processor` to `annotationProcessorPaths`

### Wrong module

Typical sign:

- parent or sibling module has the processor, but the entity module does not

First fix:

- wire the processor in the module that compiles the `@EntityProxy` classes

### IDE-only unresolved proxy

Typical sign:

- command-line build generated the files, but IDEA still shows red imports

First fix:

- refresh project model
- mark generated sources
- confirm generated package/import path

## 7. Recommended diagnosis order for the agent

When answering a user:

1. classify Java APT vs Kotlin KSP vs `@EntityFileProxy`
2. verify the processor is wired in the correct module
3. verify `@EntityProxy` is present on the target type
4. inspect whether generated output exists in the expected directory
5. if not, find the first real compile/processor error
6. only after that, discuss imports, IDE refresh, or interface style

## 8. What to say precisely

Prefer statements like:

- "`@EntityProxy` is the generation trigger; `ProxyEntityAvailable` is a usage
  pattern, not the switch that makes APT/KSP generate proxies."
- "If `annotationProcessorPaths` is already configured, easy-query must appear
  there explicitly."
- "A visible `XxxProxy` missing error is often secondary; check the first
  compile or processor failure in the build log."
- "If files already exist under generated-sources, this is now an IDE model or
  import-path problem, not a generation problem."

## Read next

- Java APT project: `setup-java.md`
- Kotlin KSP project: `setup-kotlin.md`
- broader runtime/behavior issues after generation succeeds: `troubleshooting.md`
