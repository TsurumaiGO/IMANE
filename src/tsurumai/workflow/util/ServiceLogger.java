package tsurumai.workflow.util;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.LogManager;

import javax.inject.Singleton;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
//import org.apache.commons.logging.impl.Log4JLogger;
//import org.apache.log4j.Appender;
//import org.apache.log4j.FileAppender;


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
			//if(theInstance.logger.) {
			if(theInstance.logger != null) {
				initJUL();
			}
				/* log4j2脆弱性対応のため
				Appender a = ((Log4JLogger)theInstance.logger).getLogger().getAppender("file");
				if(a instanceof FileAppender) {
					theInstance.logger.info("カレントディレクトリ: " + java.nio.file.Path.of(".").toAbsolutePath());
					FileAppender app =(FileAppender)a;
					
					String filepath = Path.of(app.getFile()).toAbsolutePath().toString();

					String dir = Path.of(app.getFile()).toAbsolutePath().getFileName().toString();
					if(!new File(filepath).canWrite()) {
						String altpath = Path.of(System.getProperty("user.home"), "logs/workshop.log").toAbsolutePath().toString();
						app.setFile(altpath);
						theInstance.logger.warn("指定されたディレクトリにログを書き込めません。代替パスを設定します:" + altpath);
						
					}else
						theInstance.logger.info("logging to: " + filepath);
				}*/
		}
		return theInstance;
	}
	
	protected static void initJUL() {
        try(InputStream in = ServiceLogger.class.getClassLoader().getResourceAsStream("logging.properties")){
            LogManager.getLogManager().readConfiguration( in );
        }catch(IOException t){
        	System.err.println("no logging.properties found.");
        }
		
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

	public  String getCaller() {
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
		if(!logger.isDebugEnabled()) return;//性能改善
		String paramstr = Util.join(params, ",");
		getLogger().debug("(" + paramstr + ") : " + msg);
	}
	public void debug(String msg) {
		if(!logger.isDebugEnabled()) return;//性能改善
		logger.debug(tm.format(new Date()) + ": " + getCaller() + msg);
	}

	public void trace(String msg, Object...params) {
		if(!logger.isTraceEnabled()) return;//性能改善
		String paramstr = Util.join(params, ",");
		logger.info(tm.format(new Date()) + ": " + getCaller() + "(" + paramstr + ") : " + msg);
	}
	
	public void trace(String msg) {
		if(!logger.isTraceEnabled()) return;//性能改善
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
