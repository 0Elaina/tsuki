package com.nep.tsuki.artist.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("artist_aliases")
public class ArtistAlias {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long artistId;
    private String name;
    private Integer position;
}
