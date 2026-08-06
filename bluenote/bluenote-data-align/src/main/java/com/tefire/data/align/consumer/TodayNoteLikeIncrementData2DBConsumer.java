package com.tefire.data.align.consumer;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Objects;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.tefire.data.align.constants.MQConstants;
import com.tefire.data.align.constants.RedisKeyConstants;
import com.tefire.data.align.constants.TableConstants;
import com.tefire.data.align.domain.mapper.InsertRecordMapper;
import com.tefire.data.align.model.dto.LikeUnlikeNoteMqDTO;
import com.tefire.framework.common.util.JsonUtils;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;


/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-08-06 14:04:56
 * @Description: 日增量数据落库：笔记点赞、取消点赞
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_data_align_" + MQConstants.TOPIC_COUNT_NOTE_LIKE,
    topic = MQConstants.TOPIC_COUNT_NOTE_LIKE
)
public class TodayNoteLikeIncrementData2DBConsumer implements RocketMQListener<String> {
    
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private InsertRecordMapper insertRecordMapper;

     /**
     * 表总分片数
     */
    @Value("${table.shards}")
    private int tableShards;
    
    @Override
    public void onMessage(String body) {
        log.info("## TodayNoteLikeIncrementData2DBConsumer 消费到了 MQ: {}", body);

        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = JsonUtils.parseObject(body, LikeUnlikeNoteMqDTO.class);
        
        if (Objects.isNull(likeUnlikeNoteMqDTO)) return;
            
        Long noteCreatorId = likeUnlikeNoteMqDTO.getNoteCreatorId();
        Long noteId = likeUnlikeNoteMqDTO.getNoteId();
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String bloomKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(date);

        // 1. 布隆过滤器判断该日增量数据是否已经记录
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_today_note_like_check.lua")));
        Long result = redisTemplate.execute(script, Collections.singletonList(bloomKey), noteId);

        // 若布隆过滤器判断不存在（绝对正确）
        if (Objects.equals(result, 0L)) {
            // 2. 若无，才会落库，减轻数据库压力

            // 根据分片总数，取模，分别获取对应的分片序号
            long userIdHashKey = noteCreatorId % tableShards;
            long noteIdHashKey = noteId % tableShards;

            // 编程式事务，保证多语句的原子性
            Boolean isSuccess = transactionTemplate.execute(status -> {
                try {
                    // 将日增量变更数据，分别写入两张表
                    // - t_data_align_note_like_count_temp_日期_分片序号
                    // - t_data_align_user_like_count_temp_日期_分片序号
                    insertRecordMapper.insert2DataAlignNoteLikeCountTempTable(TableConstants.buildTableNameSuffix(date, noteIdHashKey), noteId);
                    insertRecordMapper.insert2DataAlignUserLikeCountTempTable(TableConstants.buildTableNameSuffix(date, userIdHashKey), noteCreatorId);

                    return true;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    log.error("", e);
                }
                return false;
            });

            // 3. 数据库写入成功后，再添加布隆过滤器中
            if (Boolean.TRUE.equals(isSuccess)) {
                RedisScript<Long> bloomAddScript = RedisScript.of("return redis.call('BF.ADD', KEYS[1], ARGV[1])", Long.class);
                redisTemplate.execute(bloomAddScript, Collections.singletonList(bloomKey), noteId);
            }
        }
    }
}
