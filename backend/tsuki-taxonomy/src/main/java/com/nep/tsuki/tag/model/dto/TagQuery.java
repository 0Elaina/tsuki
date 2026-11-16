package com.nep.tsuki.tag.model.dto;

import com.nep.tsuki.api.PageQuery;
import com.nep.tsuki.tag.model.enums.TagStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TagQuery extends PageQuery {
    private String keyword;
    private TagStatus status;
}
