package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scfx.entity.ChatSession;
import com.scfx.mapper.ChatSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
public class ChatSessionService extends ServiceImpl<ChatSessionMapper, ChatSession> {

    private static final int MAX_LAST_MESSAGE_LENGTH = 100;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 查询用户会话列表（分页 + 搜索 + 时间筛选）
     * 统一过滤 is_deleted=0，按 updated_at DESC 排序
     */
    public Page<ChatSession> getSessions(String userId, int page, int size,
                                          String keyword, String start, String end) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getIsDeleted, 0);

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(ChatSession::getTitle, keyword);
        }

        if (start != null && !start.isEmpty()) {
            try {
                LocalDate startDate = LocalDate.parse(start, DATE_FORMATTER);
                wrapper.ge(ChatSession::getUpdatedAt, startDate.atStartOfDay());
            } catch (DateTimeParseException e) {
                log.warn("[ChatSession] invalid start date: {}", start);
            }
        }

        if (end != null && !end.isEmpty()) {
            try {
                LocalDate endDate = LocalDate.parse(end, DATE_FORMATTER);
                wrapper.le(ChatSession::getUpdatedAt, endDate.plusDays(1).atStartOfDay());
            } catch (DateTimeParseException e) {
                log.warn("[ChatSession] invalid end date: {}", end);
            }
        }

        wrapper.orderByDesc(ChatSession::getUpdatedAt);
        return this.page(new Page<>(page, size), wrapper);
    }

    /**
     * 获取会话详情（含不存在/已删除判断）
     */
    public ChatSession getSessionDetail(String id) {
        ChatSession session = this.getById(id);
        if (session == null || session.getIsDeleted() == 1) {
            return null;
        }
        return session;
    }

    /**
     * 更新标题（含 title_source 校验）
     *
     * @param id      会话 ID
     * @param title   新标题
     * @param source  标题来源: default/auto/manual
     * @return 更新成功返回 true；title_source=manual 拒绝返回 false
     */
    @Transactional
    public boolean updateTitle(String id, String title, String source) {
        ChatSession session = this.getById(id);
        if (session == null || session.getIsDeleted() == 1) {
            return false;
        }

        // 强制校验：manual 锁定状态拒绝任何自动更新
        if ("manual".equals(session.getTitleSource()) && !"manual".equals(source)) {
            log.warn("[ChatSession] reject auto-update for manual-titled session: id={}", id);
            return false;
        }

        session.setTitle(title);
        session.setTitleSource(source);
        return this.updateById(session);
    }

    /**
     * 批量软删除
     *
     * @param ids 会话 ID 列表
     * @return 实际更新的记录数
     */
    @Transactional
    public int batchDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        ChatSession updateEntity = new ChatSession();
        updateEntity.setIsDeleted(1);
        return this.baseMapper.update(
            updateEntity,
            new LambdaQueryWrapper<ChatSession>()
                .in(ChatSession::getId, ids)
                .eq(ChatSession::getIsDeleted, 0)
        );
    }

    /**
     * 创建或更新会话记录
     * - 首次：创建会话（title=首问前20字, title_source=default）
     * - 后续：更新 message_count+1, last_message（截断100字）
     */
    @Transactional
    public void saveOrUpdateSession(String userId, String sessionId, String question, String lastAnswer) {
        ChatSession session = this.getById(sessionId);
        if (session == null || session.getIsDeleted() == 1) {
            session = new ChatSession();
            session.setId(sessionId);
            session.setUserId(userId);
            session.setTitle(truncateTitle(question, 20));
            session.setTitleSource("default");
            session.setMessageCount(1);
            session.setLastMessage(truncateLastMessage(lastAnswer));
            session.setIsDeleted(0);
            session.setIsArchived(0);
            this.save(session);
        } else {
            session.setMessageCount(session.getMessageCount() + 1);
            session.setLastMessage(truncateLastMessage(lastAnswer));
            this.updateById(session);
        }
    }

    /**
     * last_message 截断：最大 100 字符，超长补 ...
     */
    public static String truncateLastMessage(String message) {
        if (message == null) return null;
        if (message.length() <= MAX_LAST_MESSAGE_LENGTH) return message;
        return message.substring(0, MAX_LAST_MESSAGE_LENGTH) + "...";
    }

    private static String truncateTitle(String text, int maxLen) {
        if (text == null || text.isBlank()) return "新会话";
        String clean = text.replaceAll("[\\n\\r]", " ").trim();
        return clean.length() <= maxLen ? clean : clean.substring(0, maxLen);
    }
}
