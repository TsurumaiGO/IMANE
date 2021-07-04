package tsurumai.workflow.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.inject.Singleton;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4JLogger;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;


/**ログ出力を制御する
 * 
 * ログ出力にはlog4jを使用する。設定方法については<a href = "https://logging.apache.org/log4j/1.2">ドキュメント</a>を参照。
 * ロガー名はデフォルトで"workshop"。 システムプロパティFrICORE.logger.nameで変更可能・。<br>
 * */
public class ServiceLogger {
	ServiceLogger() {

	}
	@Singleton
	static ServiceLogger theInstance = null;
	public static ServiceLogger getLogger() {
		if(theInstance == null) {
			theInstance = new ServiceLogger();
//			String loggerName = System.getProperty(LOGGER_NAME,"workshop");
			theInstance.logger = LogFactory.getLog("workshop");

				Appender a = ((Log4JLogger)theInstance.logger).getLogger().getAppender("file");
				if(a instanceof FileAppender) {
					FileAppender app =(FileAppender)a;
					theInstance.logger.info("logging to: " + new File(app.getFile()).getAbsolutePath());
				}
		}
		return theInstance;
	}
	public static final String LOGGER_NAME = "FrICORE.logger.name";
//	private static Log logger = LogFactory.getLog(System.getProperty(LOGGER_NAME,"workshop"));
	private Log logger = null;
	protected static SimpleDateFormat tm = new SimpleDateFormat("yy/MM/dd HH:mm:ss");
//	public void log(final String message) {
//		
//		String header = tm.format(new Date()) + ": ";
//		//ps.println(header + ": ["+level + "] " + message);
//	//	logger.log(Priority., message);
//		logger.info(header +message);
//	}
//	public void info(final String msg){
//		String header = tm.format(new Date()) + ": ";
//		logger.info(header + msg);
//	}
//
//	public void warn(final String message, final Throwable cause) {
//		String header = tm.format(new Date()) + ": ";
//		logger.warn(header + message, cause);
//	}
//	public void warn(final String message) {
//		String header = tm.format(new Date()) + ": ";
//		warn(header + message, null);
//	}

//	public void error(final String message, final Throwable cause) {
//		String header = tm.format(new Date()) + ": ";
//		logger.error(header + message , cause);
//	}
//
//	public void error(final String message) {
//		String header = tm.format(new Date()) + ": ";
//		logger.error(header + message);
//	}
	public void debug(final String message, final Throwable cause) {
		String header = tm.format(new Date()) + ": ";
		logger.debug(header + message, cause);
	}
	public void error(final Throwable cause){
		logger.error(cause.getMessage(), cause);
	}
//	public void debug(final String message) {
//		String header = tm.format(new Date()) + ": ";
//		logger.debug(header + message);
//	}

	public static String getCaller() {
		StackTraceElement[] st = new Throwable().getStackTrace();
		for(int i = 0; i < st.length; i ++) {
			if(st[i].getClassName().equals(Util.class.getName()) || 
				st[i].getClassName().equals(ServiceLogger.class.getName()))
				continue;
			return st[i].toString();
		}
		return "unknown";
	}
	/**次の形式でログを出力
	 * enter: 呼び出し元スタック情報 (引数,...)*/
	public void enter(Object... param) {
		info("enter:" + getCaller(), param);
	}
	public void enter() {
		info("enter:" + getCaller());
	}
	public void info(String msg) {
//		info(msg, new Object[0]);
		
		logger.info(tm.format(new Date()) + ": " + msg);
	}
	public void info(String msg, Object... params) {
		String str = String.format("%s %s (%s)", msg, getCaller(), Util.join(params, ", "));
		getLogger().info(str);
	}
	public void debug(String msg, Object...params) {
		String paramstr = Util.join(params, ",");
		getLogger().debug("(" + paramstr + ") : " + msg);
	}
	public void debug(String msg) {
		logger.debug(tm.format(new Date()) + ": " + getCaller() + msg);
	}

	public void trace(String msg, Object...params) {

		String paramstr = Util.join(params, ",");
		logger.info(tm.format(new Date()) + ": " + getCaller() + "(" + paramstr + ") : " + msg);
	}
	
	public void trace(String msg) {
		trace(msg, new Object[0]);
	}
	public void warn(String msg) {
		logger.info(tm.format(new Date()) + ": " + getCaller() + ": " + msg);
	}
	public void error(String msg, Throwable t, boolean noStackTrace) {
		logger.error(tm.format(new Date()) + ": " + getCaller() + ": " + msg);
		if(t != null && !noStackTrace)	t.printStackTrace(System.err);
	}
	
	public void error(String msg, Throwable t) {
		error(msg, t, false);
	}

	public void error(String msg) {
		error(msg, null);
	}
	
	
	
}
