package com.tefire.data.align.job;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.tefire.data.align.constants.RedisKeyConstants;
import com.tefire.data.align.constants.TableConstants;
import com.tefire.data.align.domain.mapper.DeleteMapper;
import com.tefire.data.align.domain.mapper.SelectMapper;
import com.tefire.data.align.domain.mapper.UpdateMapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;

import cn.hutool.core.collection.CollUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Description: 定时分片广播任务：对当日发生变更的用户获得点赞数进行对齐
 */
@Slf4j
@Component
public class UserLikeCountShardingXxlJob {

    @Resource
    private SelectMapper selectMapper;
    @Resource
    private UpdateMapper updateMapper;
    @Resource
    private DeleteMapper deleteMapper;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 分片广播任务
     */
    @XxlJob("userLikeCountShardingJobHandler")
    public void userLikeCountShardingJobHandler() throws Exception {
        // 获取分片参数
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的用户获得点赞数进行对齐");
        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        log.info("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 表后缀
        String date = LocalDate.now().minusDays(1) // 昨晚日期
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 表名后缀
        String tableNameSuffix = TableConstants.buildTableNameSuffix(date, shardIndex);

        // 一批次 1000 条
        int batchSize = 1000;
        // 共对齐了多少条记录，默认为 0
        int processedTotal = 0;

        for (;;) {
            // 1. 分批次查询 t_data_align_user_like_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
            List<Long> userIds = selectMapper.selectBatchFromDataAlignUserLikeCountTempTable(tableNameSuffix, batchSize);

            if (CollUtil.isEmpty(userIds)) break;

            userIds.forEach(userId -> {
                // 2: 关联 t_note + t_note_like，查询该用户发布笔记被点赞的总数
                int likeTotal = selectMapper.selectUserLikeCountFromNoteLikeTableByUserId(userId);

                // 3: 更新 t_user_count 表, 更新对应 Redis 缓存
                int count = updateMapper.updateUserLikeTotalByUserId(userId, likeTotal);
                if (count > 0) {
                    String redisKey = RedisKeyConstants.buildCountUserKey(userId);
                    boolean hasKey = redisTemplate.hasKey(redisKey);
                    if (hasKey) {
                        // 更新 Hash 中的 Field 用户获得的点赞总数
                        redisTemplate.opsForHash().put(redisKey, RedisKeyConstants.FIELD_USER_LIKE_TOTAL, likeTotal);
                    }
                }
            });

            // 4. 批量物理删除这一批次记录
            deleteMapper.batchDeleteDataAlignUserLikeCountTempTable(tableNameSuffix, userIds);
            // 当前已处理的记录数
            processedTotal += userIds.size();
        }
        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的用户获得点赞数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
