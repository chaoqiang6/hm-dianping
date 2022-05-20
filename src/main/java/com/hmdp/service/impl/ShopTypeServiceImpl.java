package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public List<ShopType> queryOrderBySort() {
        final List<String> shopTypeInCache = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        if(CollectionUtil.isNotEmpty(shopTypeInCache)){
            return shopTypeInCache.stream().map(s -> JSONUtil.toBean(s,ShopType.class)).collect(Collectors.toList());
        }
        List<ShopType> typeListInDb = query().orderByAsc("sort").list();
        if(CollectionUtil.isNotEmpty(typeListInDb)){
            stringRedisTemplate.opsForList().rightPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY,typeListInDb.stream().map(shopType -> JSONUtil.toJsonStr(shopType, JSONConfig.create().setIgnoreNullValue(true))).collect(Collectors.toList()));
            stringRedisTemplate.expire(RedisConstants.CACHE_SHOP_TYPE_KEY, Duration.ofMinutes(RedisConstants.CACHE_SHOP_TYPE_TTL));
        }
        return typeListInDb;
    }
}
