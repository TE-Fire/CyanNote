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
 * @Description: 定时分片广播任务：对当日发生变更的笔记收藏数进行对齐
 */
@Slf4j
@Component
public class NoteCollectCountShardingXxlJob {

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
    @XxlJob("noteCollectCountShardingJobHandler")
    public void noteCollectCountShardingJobHandler() throws Exception {
        // 获取分片参数
        // 分片序号
        int shardIndex = XxlJobHelper.getShardIndex();
        // 分片总数
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("=================> 开始定时分片广播任务：对当日发生变更的笔记收藏数进行对齐");
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
            // 1. 分批次查询 t_data_align_note_collect_count_temp_日期_分片序号，如一批次查询 1000 条，直到全部查询完成
            List<Long> noteIds = selectMapper.selectBatchFromDataAlignNoteCollectCountTempTable(tableNameSuffix, batchSize);

            if (CollUtil.isEmpty(noteIds)) break;

            noteIds.forEach(noteId -> {
                // 2: 对 t_note_collection 收藏表执行 count(*) 操作，获取收藏总数
                int collectTotal = selectMapper.selectCountFromNoteCollectionTableByUserId(noteId);

                // 3: 更新 t_note_count 表, 更新对应 Redis 缓存
                int count = updateMapper.updateNoteCollectTotalByUserId(noteId, collectTotal);
                if (count > 0) {
                    String redisKey = RedisKeyConstants.buildCountNoteKey(noteId);
                    boolean hasKey = redisTemplate.hasKey(redisKey);
                    if (hasKey) {
                        // 更新 Hash 中的 Field 笔记收藏总数
                        redisTemplate.opsForHash().put(redisKey, RedisKeyConstants.FIELD_COLLECT_TOTAL, collectTotal);
                    }
                }
            });

            // 4. 批量物理删除这一批次记录
            deleteMapper.batchDeleteDataAlignNoteCollectCountTempTable(tableNameSuffix, noteIds);
            // 当前已处理的记录数
            processedTotal += noteIds.size();
        }
        XxlJobHelper.log("=================> 结束定时分片广播任务：对当日发生变更的笔记收藏数进行对齐，共对齐记录数：{}", processedTotal);
    }
}
