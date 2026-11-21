package com.nep.tsuki.artist.service.impl;

import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nep.tsuki.artist.constants.ArtistApiCode;
import com.nep.tsuki.artist.constants.ArtistConstants;
import com.nep.tsuki.artist.mapper.ArtistAliasMapper;
import com.nep.tsuki.artist.mapper.ArtistMapper;
import com.nep.tsuki.artist.mapper.ArtistProfileMapper;
import com.nep.tsuki.artist.model.dto.ArtistCreateRequest;
import com.nep.tsuki.artist.model.entity.Artist;
import com.nep.tsuki.artist.model.entity.ArtistAlias;
import com.nep.tsuki.artist.model.entity.ArtistProfile;
import com.nep.tsuki.artist.model.vo.ArtistResponse;
import com.nep.tsuki.artist.service.ArtistService;
import com.nep.tsuki.common.constants.ZoneConstants;
import com.nep.tsuki.common.exceptions.BusinessException;
import com.nep.tsuki.common.utils.RegionUtils;
import com.nep.tsuki.common.utils.StrUtils;
import com.nep.tsuki.common.utils.UriUtils;
import com.nep.tsuki.media.image.model.vo.BoundImage;
import com.nep.tsuki.media.image.service.MediaImageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistServiceImpl implements ArtistService {
    private final ArtistMapper artistMapper;
    private final ArtistAliasMapper artistAliasMapper;
    private final ArtistProfileMapper artistProfileMapper;
    private final MediaImageService mediaImageService;

    /**
     * 创建艺人
     * 
     * @param request 艺人创建请求
     * @return 艺人响应
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArtistResponse create(ArtistCreateRequest request) {
        String countryCode = RegionUtils.getNormalizedCountryCode(request.countryCode());
        String biography = StrUtils.stripToNull(request.biography());
        Integer debutYear = validateDebutYear(request.debutYear());
        String officialWebsiteUrl = UriUtils.normalizeAndValidateHttpUrl(request.officialWebsiteUrl(),
                ArtistConstants.MAX_WEBSITE_URL_LENGTH);

        // 清洗输入的别名列表
        List<String> aliases = request.aliases() == null
                ? List.of()
                : request.aliases().stream().map(String::strip).toList();
        Set<String> seenAliases = new HashSet<>(aliases.size());
        for (String alias : aliases) {
            if (!seenAliases.add(alias.toLowerCase(Locale.ROOT))) {
                throw new BusinessException(ArtistApiCode.DUPLICATE_ARTIST_ALIAS);
            }
        }

        BoundImage boundAvatar = null;
        // 绑定头像
        if (request.avatarAssetId() != null) {
            boundAvatar = mediaImageService.bindImage(request.avatarAssetId());
        }

        // 组装插入艺人
        Artist artist = new Artist();
        artist.setName(request.name().strip());
        artist.setSortName(StrUtils.stripToNull(request.sortName()));
        artist.setType(request.type().name());
        artist.setAvatarAssetId(boundAvatar == null ? null : boundAvatar.assetId());
        artist.setAvatarUrl(boundAvatar == null ? null : boundAvatar.relativePath());
        artistMapper.insert(artist);

        // 资料表
        ArtistProfile profile = null;
        if (countryCode != null || biography != null || debutYear != null || officialWebsiteUrl != null) {
            profile = new ArtistProfile();
            profile.setArtistId(artist.getId());
            profile.setCountryCode(countryCode);
            profile.setBiography(biography);
            profile.setDebutYear(debutYear);
            profile.setOfficialWebsiteUrl(officialWebsiteUrl);
            artistProfileMapper.insert(profile);
        }

        // 批量构建并插入艺人别名
        List<ArtistAlias> artistAliasList = new ArrayList<>(aliases.size());
        if (!aliases.isEmpty()) {
            for (int i = 0; i < aliases.size(); i++) {
                ArtistAlias artistAlias = new ArtistAlias();
                artistAlias.setArtistId(artist.getId());
                artistAlias.setName(aliases.get(i));
                artistAlias.setPosition(i);
                artistAliasList.add(artistAlias);
            }
            artistAliasMapper.insert(artistAliasList);
        }

        log.info("新建艺人成功, id={}, name={}, 别名数量={}", artist.getId(), artist.getName(), aliases.size());
        return ArtistResponse.of(artist, profile, aliases, boundAvatar == null ? null : boundAvatar.url());
    }

    private Integer validateDebutYear(Integer year) {
        if (year == null)
            return null;
        int maxYear = Year.now(ZoneId.of(ZoneConstants.SHANGHAI)).getValue();
        if (year < 1 || year > maxYear) {
            throw new BusinessException(ArtistApiCode.INVALID_ARTIST_DEBUT_YEAR);
        }
        return year;
    }
}
