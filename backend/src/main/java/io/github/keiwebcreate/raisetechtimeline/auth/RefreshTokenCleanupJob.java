package io.github.keiwebcreate.raisetechtimeline.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 期限の切れたリフレッシュトークンを、1日1回まとめて消す（テーブルが増え続けないように）。
 */
@Component
public class RefreshTokenCleanupJob {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);

	private final RefreshTokenService refreshTokenService;

	public RefreshTokenCleanupJob(RefreshTokenService refreshTokenService) {
		this.refreshTokenService = refreshTokenService;
	}

	/** 毎日 4:00（UTC）。利用者の少ない時間 */
	@Scheduled(cron = "0 0 4 * * *", zone = "UTC")
	public void deleteExpired() {
		int count = refreshTokenService.deleteExpired();
		log.info("期限切れのリフレッシュトークンを {} 件消しました", count);
	}

}
