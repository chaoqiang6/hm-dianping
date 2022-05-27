package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService followService;

    @GetMapping("/or/not/{userId}")
    public Result isFollow(@PathVariable("userId") Long userId){
        return followService.isFollow(userId);
    }

    @PutMapping("{userId}/{isFollow}")
    public Result follow(@PathVariable("userId") Long userId,@PathVariable("isFollow")Boolean isFollow){
        return followService.followOrNot(userId,isFollow);
    }

    @GetMapping("common/{userId}")
    public Result getCommonUsers(@PathVariable("userId") Long userId){
        return followService.getCommonUsers(userId);
    }



}
