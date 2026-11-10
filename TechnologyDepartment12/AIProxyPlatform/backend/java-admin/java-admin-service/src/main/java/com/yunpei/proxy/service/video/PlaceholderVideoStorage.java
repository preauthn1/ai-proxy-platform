package com.yunpei.proxy.service.video;

import com.yunpei.proxy.service.image.TransferResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 占位视频存储：直通 sourceUrl。
 *
 * @author yunpei
 */
@Component
@ConditionalOnProperty(prefix = "oss", name = "enabled", havingValue = "false", matchIfMissing = true)
public class PlaceholderVideoStorage implements VideoStorage {

    @Override
    public TransferResult transfer(String sourceUrl, String jobId) {
        return new TransferResult(sourceUrl, null);
    }
}
