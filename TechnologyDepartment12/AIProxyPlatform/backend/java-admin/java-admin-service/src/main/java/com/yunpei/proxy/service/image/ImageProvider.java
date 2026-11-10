package com.yunpei.proxy.service.image;

/**
 * 图片生成 Provider 抽象：接收 {@link ImageBrief}，返回一张 {@link GeneratedImage}。
 *
 * <p>实现：{@link PlaceholderImageProvider}、{@link New2ApiImageProvider}；
 * 通过 {@link ImageProviderFactory} 按 {@code image.provider} 路由选择。
 *
 * @author yunpei
 */
public interface ImageProvider {

    /**
     * 根据 brief 生成一张图片。
     *
     * @param brief 单张图生成请求
     * @return 生成结果图
     */
    GeneratedImage generate(ImageBrief brief);
}
