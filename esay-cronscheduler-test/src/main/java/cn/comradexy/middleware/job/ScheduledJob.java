package cn.comradexy.middleware.job;

import cn.comradexy.middleware.ecs.annotation.EzScheduled;
import cn.comradexy.middleware.ecs.annotation.EzSchedules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 定时任务测试用例
 *
 * @Author: ComradeXY
 * @CreateTime: 2024-07-29
 * @Description: 定时任务测试用例
 */
@Component
public class ScheduledJob {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

//    @EzSchedules({
//            @EzScheduled(cron = "0/4 * * * * ?", desc = "每4秒执行一次", maxExecCount = 100),
//            @EzScheduled(cron = "0/2 * * * * ?", desc = "每2秒执行一次", maxExecCount = 100)
//    })
    @EzScheduled(cron = "0/4 * * * * ?", desc = "每4秒执行一次", maxExecCount = 100)
    public void test() {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        logger.info("{}: 定时任务执行", currentTime);
    }
}
