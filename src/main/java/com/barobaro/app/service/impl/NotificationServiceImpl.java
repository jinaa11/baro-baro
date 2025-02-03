package com.barobaro.app.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.barobaro.app.mapper.NotificationMapper;
import com.barobaro.app.service.NotificationService;
import com.barobaro.app.vo.NotificationVO;

@Service
public class NotificationServiceImpl implements NotificationService {
	@Autowired
	private NotificationMapper mapper;

	private Map<Integer, SseEmitter> emitters = new ConcurrentHashMap<Integer, SseEmitter>();
	private ExecutorService executor = Executors.newCachedThreadPool();

	@Override
	public void addNotification(NotificationVO nvo) {
		mapper.insertNotification(nvo);
	}

	@Override
	public List<NotificationVO> getUnreadNotifications(int userSeq) {
		return mapper.selectUnreadNotifications(userSeq);
	}

	@Override
	public void markNotificationAsRead(int notificationSeq) {
		mapper.markAsRead(notificationSeq);
	}

	@Override
	public SseEmitter subscribe(int userSeq) {
		SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30분 타임아웃
		emitters.put(userSeq, emitter);

		emitter.onTimeout(() -> emitters.remove(userSeq));
		emitter.onCompletion(() -> emitters.remove(userSeq));

		System.out.println("SSE 연결이 열렸습니다. 사용자 ID: " + userSeq);
		return emitter;
	}

	@Override
	public void sendNotification(NotificationVO nvo) {
		// 사용자에게 SSE로 알림 전송
		SseEmitter emitter = emitters.get(nvo.getUserSeq());

		if (emitter == null) {
			System.err.println("Emitter 없음. 사용자: " + nvo.getUserSeq());
			return;
		}

		executor.execute(() -> {
			try {
				String notificationJson = new ObjectMapper().writeValueAsString(nvo);
				emitter.send(SseEmitter.event().name("notification").data(notificationJson));
				System.out.println("Notification sent to user: " + nvo.getUserSeq());
			} catch (IOException e) {
				System.err.println("Error sending notification: " + e.getMessage());
				emitters.remove(nvo.getUserSeq());
			}
		});

	}

}
