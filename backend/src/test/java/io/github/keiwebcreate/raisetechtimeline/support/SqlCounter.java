package io.github.keiwebcreate.raisetechtimeline.support;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

/**
 * MyBatis が DB へ送った SQL の数を数える（テスト用）。N+1 問題（一覧の件数だけ SQL が増える）がないかを確かめる。
 *
 * MyBatis の Interceptor（SQL を送る前に割り込む仕組み）で、SQL を DB に用意する（prepare）たびに1つ数える。
 * Spring の Bean にすると、MyBatis の Spring Boot の道具が自動で組み込む。
 */
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }))
public class SqlCounter implements Interceptor {

	private final AtomicInteger count = new AtomicInteger();

	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		count.incrementAndGet();
		return invocation.proceed();
	}

	public void reset() {
		count.set(0);
	}

	public int count() {
		return count.get();
	}

}
