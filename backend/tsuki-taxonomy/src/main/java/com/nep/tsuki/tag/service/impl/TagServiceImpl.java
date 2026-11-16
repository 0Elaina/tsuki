package com.nep.tsuki.tag.service.impl;

import java.util.Locale;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nep.tsuki.api.PageResult;
import com.nep.tsuki.exceptions.BusinessException;
import com.nep.tsuki.tag.constants.TagApiCode;
import com.nep.tsuki.tag.mapper.TagMapper;
import com.nep.tsuki.tag.model.dto.TagQuery;
import com.nep.tsuki.tag.model.entity.Tag;
import com.nep.tsuki.tag.model.enums.TagStatus;
import com.nep.tsuki.tag.model.vo.TagResponse;
import com.nep.tsuki.tag.service.TagService;
import com.nep.tsuki.utils.SqlUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {
    private final TagMapper tagMapper;

    /**
     * 创建标签
     *
     * @param name      标签名称
     * @param sortOrder 排序顺序
     * @return 标签信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagResponse create(String name, Integer sortOrder) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setStatus(TagStatus.ACTIVE.name());
        tag.setSortOrder(Objects.requireNonNullElse(sortOrder, 0));

        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(TagApiCode.TAG_NAME_CONFLICT);
        }
        log.info("创建标签成功: id={}, name={}", tag.getId(), tag.getName());
        return TagResponse.of(tag);
    }

    /**
     * 根据 ID 查询标签
     *
     * @param id 标签 ID
     * @return 标签信息
     */
    @Override
    public TagResponse findById(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(TagApiCode.TAG_NOT_FOUND);
        }
        return TagResponse.of(tag);
    }

    /**
     * 分页查询标签列表
     *
     * @param query 查询参数
     * @return 标签列表
     */
    @Override
    public PageResult<TagResponse> list(TagQuery query) {
        Page<Tag> page = query.toPage();

        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(Tag::getStatus, query.getStatus().name());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.apply("LOWER(name) LIKE {0} ESCAPE '!'",
                    SqlUtils.toContainsLikePattern(query.getKeyword().strip().toLowerCase(Locale.ROOT)));
        }
        tagMapper.selectPage(page, wrapper.orderByAsc(Tag::getSortOrder).orderByAsc(Tag::getId));
        return PageResult.from(page.convert(TagResponse::of));
    }

    /**
     * 更新标签
     *
     * @param id        标签 ID
     * @param name      标签名称
     * @param sortOrder 排序顺序
     * @return 标签信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagResponse update(Long id, String name, Integer sortOrder) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        tag.setSortOrder(sortOrder);

        int updated;
        try {
            updated = tagMapper.updateById(tag);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(TagApiCode.TAG_NAME_CONFLICT);
        }

        if (updated == 0) {
            throw new BusinessException(TagApiCode.TAG_NOT_FOUND);
        }

        log.info("更新标签成功: id={}, name={}", tag.getId(), tag.getName());
        return findById(id);
    }

    /**
     * 更新标签状态
     *
     * @param id     标签 ID
     * @param status 标签状态
     * @return 标签信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TagResponse updateStatus(Long id, TagStatus status) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setStatus(status.name());

        int updated = tagMapper.updateById(tag);
        if (updated == 0) {
            throw new BusinessException(TagApiCode.TAG_NOT_FOUND);
        }
        log.info("更新标签状态成功: id={}, status={}", tag.getId(), tag.getStatus());
        return findById(id);
    }

    /**
     * 删除标签
     *
     * @param id 标签 ID
     */
    @Override
    public void delete(Long id) {
        int deleted = tagMapper.deleteById(id);
        if (deleted == 0) {
            log.warn("目标标签不存在, 幂等成功, id={}", id);
            return;
        }
        log.info("删除目标标签成功, id={}", id);
    }
}
