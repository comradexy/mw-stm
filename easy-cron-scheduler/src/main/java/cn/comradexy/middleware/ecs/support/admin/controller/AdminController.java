package cn.comradexy.middleware.ecs.support.admin.controller;

import cn.comradexy.middleware.ecs.support.admin.service.IScheduleService;
import cn.comradexy.middleware.ecs.support.admin.domain.Request;
import cn.comradexy.middleware.ecs.support.admin.domain.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 管理端controller
 *
 * @Author: ComradeXY
 * @CreateTime: 2024-08-13
 * @Description: 管理端controller
 */
@ResponseBody
@CrossOrigin
@RequestMapping("/schedule/api")
public class AdminController {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    private IScheduleService scheduleService;

    @GetMapping("/list")
    public Result<?> list() {
        try {
            logger.info("[EasyCronScheduler] Query all tasks.");
            return Result.success(scheduleService.queryAllTasks());
        } catch (Exception e) {
            logger.error("[EasyCronScheduler] Failed to query all tasks", e);
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/query")
    public Result<?> query(@RequestParam("taskKey") String taskKey) {
        try {
            logger.info("[EasyCronScheduler] Query task, task key: {}", taskKey);
            return Result.success(scheduleService.queryTask(taskKey));
        } catch (Exception e) {
            logger.error("[EasyCronScheduler] Failed to query task, task key: {}", taskKey, e);
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/cancel")
    public Result<?> cancel(@RequestBody Request request) {
        try {
            logger.info("[EasyCronScheduler] Cancel task, task key: {}", request.getTaskKey());
            scheduleService.cancelTask(request.getTaskKey());
            return Result.success();
        } catch (Exception e) {
            logger.error("[EasyCronScheduler] Failed to cancel task, task key: {}", request.getTaskKey(), e);
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/pause")
    public Result<?> pause(@RequestBody Request request) {
        try {
            logger.info("[EasyCronScheduler] Pause task, task key: {}", request.getTaskKey());
            scheduleService.pasueTask(request.getTaskKey());
            return Result.success();
        } catch (Exception e) {
            logger.error("[EasyCronScheduler] Failed to pause task, task key: {}", request.getTaskKey(), e);
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/resume")
    public Result<?> resume(@RequestBody Request request) {
        try {
            logger.info("[EasyCronScheduler] Resume task, task key: {}", request.getTaskKey());
            scheduleService.resumeTask(request.getTaskKey());
            return Result.success();
        } catch (Exception e) {
            logger.error("[EasyCronScheduler] Failed to resume task, task key: {}", request.getTaskKey(), e);
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/schedule")
    public Result<?> schedule(@RequestBody Request request) {
        try {
            logger.info("[EasyCronScheduler] Schedule task, task key: {}", request.getTaskKey());
            scheduleService.scheduleTask(request.getTaskKey());
            return Result.success();
        } catch (Exception e) {
            logger.error("[EasyCronScheduler] Failed to schedule task, task key: {}", request.getTaskKey(), e);
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/delete")
    public Result<?> delete(@RequestBody Request request) {
        try {
            logger.info("[EasyCronScheduler] Delete task, task key: {}", request.getTaskKey());
            scheduleService.deleteTask(request.getTaskKey());
            return Result.success();
        } catch (Exception e) {
            logger.error("[EasyCronScheduler] Failed to delete task, task key: {}", request.getTaskKey(), e);
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/query_handler")
    public Result<?> queryHandler(@RequestParam("handlerKey") String handlerKey) {
        try {
            logger.info("[EasyCronScheduler] Query handler, handler key: {}", handlerKey);
            return Result.success(scheduleService.queryHandler(handlerKey));
        } catch (Exception e) {
            logger.error("[EasyCronScheduler] Failed to query handler, handler key: {}", handlerKey, e);
            return Result.fail(e.getMessage());
        }
    }
}
