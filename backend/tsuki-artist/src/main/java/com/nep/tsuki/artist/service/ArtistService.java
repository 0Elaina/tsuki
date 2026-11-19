package com.nep.tsuki.artist.service;

import com.nep.tsuki.artist.model.dto.ArtistCreateRequest;
import com.nep.tsuki.artist.model.vo.ArtistResponse;

public interface ArtistService {
    /**
     * 创建艺人
     * 
     * @param request 艺人创建请求
     * @return 艺人响应
     */
    ArtistResponse create(ArtistCreateRequest request);
}
