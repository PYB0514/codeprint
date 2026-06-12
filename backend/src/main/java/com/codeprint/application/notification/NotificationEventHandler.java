// 도메인 이벤트를 수신하여 알림을 생성하는 이벤트 핸들러
package com.codeprint.application.notification;

import com.codeprint.domain.community.CommentAddedEvent;
import com.codeprint.domain.community.PostLikedEvent;
import com.codeprint.domain.message.MessageSentEvent;
import com.codeprint.domain.user.UserFollowedEvent;
import com.codeprint.infrastructure.push.WebPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationService notificationService;
    private final NotificationSettingsApplicationService notificationSettingsService;
    private final WebPushService webPushService;

    // 쪽지 수신 알림 생성 + 수신자 push 설정에 따라 웹 푸시 발송
    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        notificationService.create(event.receiverId(), "DM",
                event.senderUsername() + "님이 쪽지를 보냈습니다.", "/messages");
        if (notificationSettingsService.isDmPushEnabled(event.receiverId())) {
            String preview = event.content().length() > 60
                    ? event.content().substring(0, 60) + "…"
                    : event.content();
            webPushService.sendToUser(event.receiverId(), event.senderUsername() + "님의 쪽지", preview);
        }
    }

    // 팔로우 알림 생성
    @EventListener
    public void onUserFollowed(UserFollowedEvent event) {
        notificationService.create(event.followingId(), "FOLLOW",
                event.followerUsername() + "님이 팔로우했습니다.", "/users/" + event.followerId());
    }

    // 댓글 알림 생성
    @EventListener
    public void onCommentAdded(CommentAddedEvent event) {
        if (!event.commenterId().equals(event.postOwnerId())) {
            notificationService.create(event.postOwnerId(), "COMMENT",
                    event.commenterUsername() + "님이 댓글을 달았습니다.", "/community?postId=" + event.postId());
        }
    }

    // 좋아요 알림 생성
    @EventListener
    public void onPostLiked(PostLikedEvent event) {
        if (!event.likerId().equals(event.postOwnerId())) {
            notificationService.create(event.postOwnerId(), "LIKE",
                    event.likerUsername() + "님이 게시글을 좋아합니다.", "/community?postId=" + event.postId());
        }
    }
}
