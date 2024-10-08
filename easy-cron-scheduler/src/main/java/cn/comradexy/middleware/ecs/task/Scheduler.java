package cn.comradexy.middleware.ecs.task;

import cn.comradexy.middleware.ecs.common.ScheduleContext;
import cn.comradexy.middleware.ecs.domain.ExecDetail;
import cn.comradexy.middleware.ecs.domain.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务调度器
 *
 * @Author: ComradeXY
 * @CreateTime: 2024-07-22
 * @Description: 定时任务调度器
 */
public class Scheduler implements DisposableBean {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final TaskScheduler taskScheduler;

    /**
     * 已被调度的任务
     */
    private final Map<String, ScheduledTask> scheduledTasks = new ConcurrentHashMap<>(64);

    /**
     * 任务存储区
     */
    private final TaskStore taskStore;

    public Scheduler(TaskScheduler taskScheduler, TaskStore taskStore) {
        Assert.notNull(taskScheduler, "TaskScheduler must not be null");
        this.taskScheduler = taskScheduler;

        Assert.notNull(taskStore, "TaskStore must not be null");
        this.taskStore = taskStore;
    }

    /**
     * 创建并启动任务
     *
     * @param taskKey 任务ID
     */
    @Retryable(value = TaskRejectedException.class, maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2)
            , recover = "taskRejected")
    public void scheduleTask(String taskKey) {
        clearInvalidTasks();

        // 获取任务信息
        ExecDetail execDetail = taskStore.getExecDetail(taskKey);
        TaskHandler taskHandler = taskStore.getTaskHandler(execDetail.getTaskHandlerKey());

        if (null != scheduledTasks.get(taskKey)) {
            logger.error("[EasyCronScheduler] Task: [key-{}] is already running, unable to resume", taskKey);
            return;
        }

        // 检查任务状态是否为INIT
        if (!ExecDetail.ExecState.INIT.equals(execDetail.getState())) {
            logger.error("[EasyCronScheduler] Task: [key-{}] is not in INIT state, unable to schedule", taskKey);
            return;
        }

        // 启动任务
        runTask(taskHandler, execDetail);
    }


    /**
     * 重启任务
     *
     * @param taskKey 任务ID
     */
    @Retryable(value = TaskRejectedException.class, maxAttempts = 5, backoff = @Backoff(delay = 1000, multiplier = 2)
            , recover = "taskRejected")
    public void resumeTask(String taskKey) {
        clearInvalidTasks();

        // 获取任务信息
        ExecDetail execDetail = taskStore.getExecDetail(taskKey);
        TaskHandler taskHandler = taskStore.getTaskHandler(execDetail.getTaskHandlerKey());

        if (null != scheduledTasks.get(taskKey)) {
            logger.error("[EasyCronScheduler] Task: [key-{}] is already running, unable to resume", taskKey);
            return;
        }

        // 允许恢复的任务的状态包括：PAUSED、BLOCKED、RUNNING
        // 如果发生故障时，数据库中的任务状态为RUNNING，需要恢复任务
        if (!(ExecDetail.ExecState.PAUSED.equals(execDetail.getState())
                || ExecDetail.ExecState.BLOCKED.equals(execDetail.getState())
                || ExecDetail.ExecState.RUNNING.equals(execDetail.getState()))) {
            logger.error("[EasyCronScheduler] Task: [key-{}] is not in PAUSED/BLOCKED/RUNNING state, unable to resume",
                    taskKey);
            return;
        }

        // 启动任务
        runTask(taskHandler, execDetail);
    }

    private void taskRejected(TaskRejectedException e, String taskKey) {
        logger.error("[EasyCronScheduler] Task rejected, task key: {}", taskKey, e);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        taskStore.updateExecState2Error(taskKey, "Task rejected, caused by: \n" + sw);
    }

    /**
     * 暂停任务
     *
     * @param taskKey 任务ID
     */
    public void pauseTask(String taskKey) {
        // 删除缓存中的任务
        cancelTask(taskKey);
        // 更新任务状态为暂停
        taskStore.updateExecState(taskKey, ExecDetail.ExecState.PAUSED);
    }

    /**
     * 删除任务
     *
     * @param taskKey 任务ID
     */
    public void deleteTask(String taskKey) {
        // 删除缓存中的任务
        cancelTask(taskKey);
        // 删除存储区中的任务
        taskStore.deleteExecDetail(taskKey);
    }

    /**
     * 取消任务
     */
    void cancelTask(String taskKey) {
        ScheduledTask scheduledTask = scheduledTasks.remove(taskKey);
        if (null != scheduledTask) {
            scheduledTask.cancel();
        }
    }

    private void clearInvalidTasks() {
        // 清空缓存中失效的任务
        scheduledTasks.forEach((key, value) -> {
            if (!ExecDetail.ExecState.RUNNING.equals(taskStore.getExecDetail(key).getState())) {
                if (!value.isCancelled()) value.cancel();
                scheduledTasks.remove(key);
            }
        });
    }

    private void runTask(TaskHandler taskHandler, ExecDetail execDetail) {
        String taskKey = execDetail.getKey();

        // 检查执行次数是否超过最大允许执行次数
        if (execDetail.getExecCount() >= execDetail.getMaxExecCount()) {
            logger.error("[EasyCronScheduler] Task: [key-{}] has reached the maximum number of executions, " +
                    "unable to start", taskKey);
            taskStore.deleteExecDetail(taskKey);
            return;
        }

        // 组装任务
        Object bean = getBean(taskHandler.getBeanName(), taskHandler.getBeanClassName());
        if (null == bean) {
            logger.error("[EasyCronScheduler] Task: [key-{}] failed to start, unable to get bean", taskKey);
            taskStore.updateExecState2Error(taskKey, "Unable to get bean");
            return;
        }
        Method method = ReflectionUtils.findMethod(bean.getClass(), taskHandler.getMethodName());
        if (null == method) {
            logger.error("[EasyCronScheduler] Task: [key-{}] failed to start, unable to get method", taskKey);
            taskStore.updateExecState2Error(taskKey, "Unable to get method");
            return;
        }
        Runnable runnable = () -> {
            ReflectionUtils.makeAccessible(method);
            try {
                method.invoke(bean);
            } catch (IllegalAccessException | InvocationTargetException e) {
                // ScheduledRunnable 会捕获并处理异常，这里不需要再处理，只需要抛出异常即可
                throw new RuntimeException(e);
            }
        };
        SchedulingRunnable schedulingRunnable = new SchedulingRunnable(taskKey, runnable);

        // 创建定时任务
        CronTask cronTask = new CronTask(schedulingRunnable, execDetail.getCronExpr());
        ScheduledTask scheduledTask = new ScheduledTask(cronTask);
        try {
            scheduledTask.future = taskScheduler.schedule(cronTask.getRunnable(), cronTask.getTrigger());
        } catch (TaskRejectedException ex) {
            logger.error("[EasyCronScheduler] Task: [key-{}] failed to start, task rejected. Waiting for retry...",
                    taskKey);
            taskStore.updateExecState(taskKey, ExecDetail.ExecState.BLOCKED);
            throw ex;
        }

        // 保存定时任务
        scheduledTasks.put(taskKey, scheduledTask);

        // 更新任务状态为运行中
        taskStore.updateExecState(taskKey, ExecDetail.ExecState.RUNNING);
    }

    private Object getBean(String beanName, String beanClassName) {
        try {
            Class<?> beanClass = Class.forName(beanClassName);
            // 先根据类型获取，再根据名称获取
            try {
                return ScheduleContext.applicationContext.getBean(beanClass);
            } catch (NoUniqueBeanDefinitionException ex) {
                logger.trace("[EasyCronScheduler] Bean of type {} is not unique", beanClass.getName());
                try {
                    return ScheduleContext.applicationContext.getBean(beanClass, beanName);
                } catch (NoSuchBeanDefinitionException ex2) {
                    logger.debug("[EasyCronScheduler] Bean of type {} with name {} not found",
                            beanClass.getName(), beanName);
                    return null;
                }
            } catch (NoSuchBeanDefinitionException ex) {
                logger.debug("[EasyCronScheduler] Bean of type {} not found", beanClass.getName());
                return null;
            }
        } catch (ClassNotFoundException ex) {
            logger.debug("[EasyCronScheduler] Bean class {} not found", beanClassName);
            return null;
        }
    }

    @Override
    public void destroy() {
        scheduledTasks.keySet().forEach(key -> {
            // 删除缓存中的任务
            ScheduledTask scheduledTask = scheduledTasks.remove(key);
            // 停止正在执行任务
            if (null != scheduledTask) {
                scheduledTask.cancel();
            }
            // 任务状态保持为RUNNING，方便下次启动时继续执行
        });
    }
}
