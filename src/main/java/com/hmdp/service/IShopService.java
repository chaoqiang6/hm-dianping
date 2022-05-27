package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    void update(Shop shop);

    /**
     * 根据id查询店铺
     * @param id
     * @return
     */
    Result queryShopById(Long id);

    /**
     * 根据商品类型查询店铺
     * @param typeId 类型id
     * @param current 当前页
     * @param x x坐标
     * @param y y坐标
     * @return
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
