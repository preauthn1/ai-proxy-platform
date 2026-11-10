package com.yunpei.proxy.service.video;

/**
 * 视频源图（首帧/尾帧等）。
 *
 * @param id 源图 id
 * @param url 可访问 URL
 * @param role first_frame | last_frame | reference
 * @author yunpei
 */
public record VideoSourceImage(String id, String url, String role) {}
