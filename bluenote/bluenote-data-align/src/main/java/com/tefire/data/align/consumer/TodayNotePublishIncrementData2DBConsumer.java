package com.tefire.data.align.consumer;

import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import com.tefire.data.align.constants.MQConstants;

import lombok.extern.slf4j.Slf4j;

/*
 * @Author: TE-Fire 3037749727@qq.com
 * @Date: 2026-08-06 22:09:38
 * @Description: 日增量数据落库：笔记发布、删除
 */
@Component
@Slf4j
@RocketMQMessageListener(consumerGroup = "bluenote_group_data_align_" + MQConstants.TOPIC_NOTE_OPERATE,
    topic = MQConstants.TOPIC_NOTE_OPERATE
)
public class TodayNotePublishIncrementData2DBConsumer implements RocketMQListener<String>  {
    
    @Override
    public void onMessage(String body) {
        log.info("## TodayNotePublishIncrementData2DBConsumer 消费到了 MQ: {}", body);
        // TODO: 1. 布隆过滤器判断该日增量数据是否已经记录

        // TODO: 2. 若无，才会落库，减轻数据库压力

        // 将日增量变更数据，写入日增量表中
        // - t_data_align_note_publish_count_temp_日期_分片序号

        // TODO: 3. 数据库写入成功后，再添加布隆过滤器中
    }
}
