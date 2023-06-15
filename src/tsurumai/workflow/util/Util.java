package tsurumai.workflow.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.bind.DatatypeConverter;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;

import tsurumai.workflow.WorkflowInstance;
import tsurumai.workflow.WorkflowService;
import tsurumai.workflow.model.ReplyData;

public class Util {
	/**配列から要素を探して添字を返す。見つからなければ-1を返す。評価はequalsで行う。elementはnullでも良い。arrayはnull*/
	public static int indexOf(final Object[] array, final Object element){
		for(int i = 0; i < array.length; i ++){
			Object cur = array[i];
			if(cur.toString().equals(element.toString()))return i;
		}
		return -1;
	}
	/**Util.indexOf(array, element) !=-1 を返す*/
	public static boolean contains(final Object[] array, final Object element){
		return Util.indexOf(array, element) !=-1;
	}
	public static int indexOf(final List<Object> array, final Object element){
		Object[] src = array == null ? new Object[0] : array.toArray(new Object[array.size()]);
		return indexOf(src, element);
	}
	public static boolean contains(final List<Object> array, final Object element){
		return indexOf(array, element) != -1;
	}
	/**コンテキストパス配下のファイル/ディレクトリがあるかテスト*/
	public static boolean dataExists(final String relative){
		String p = WorkflowService.getContextRelativePath(relative);
		if(p == null)return false;
		return new File(p).exists();
	}
	/**ディレクトリトラバーサル対策
	 * 
	 * <br>
	 * 注意:<ul>
	 * <li>入力が\\だとgetCannonicalPathでこける。(いいのかなぁ。)
	 * <li>UNCはNG扱いになる(まあいいか。)
	 * <li>ファイル名が.で終わる場合、末尾の.が削除される(windowsだと普通の方法ではファイルを削除できなくなるので、まあいいとする)
	 * </ul>
	 * @return 正規化されたフルパス
	 * @throws java.io.IOExcepton 正規化後のパスが元のパスと異なる場合は例外
	 * */
	public static String canonicalizePath(final String path) throws IOException{
		File full = new File(path).getAbsoluteFile();//一旦フルパスに
		String f1 = full.getAbsolutePath().replaceAll("\\\\", "/");//\を/にそろえる
		f1 = f1.replaceAll("\\/+", "/");	// //とか///とかを/に
		f1 = f1.replaceAll("\\/\\.+\\/", "/");	//    /./か/../を/に(間に..があるとき対策)
		f1 = f1.replaceAll("\\/\\.+$", "/");	//	/.か/..を/に(最後が..のとき対策)
		String can1 = new File(f1).getCanonicalPath();//そのうえで正規化
		String can2 = new File(path).getAbsoluteFile().getCanonicalPath();//元のパス名をフルパス化して正規化
		if(can1.equalsIgnoreCase(can2))	//(windows用に)ケースなしで一致したらok
			return can2; 
			
		throw new IOException("危険なファイルパスが指定されました: " + can1 + " -> " +can2);

	}
	
	/**テキストファイルをロードして内容を返す*/
	public static String readAll(final String path) throws IOException {
		return loadTextFile(path);
	}
	/**ファイルを保存する*/
	public static void save(final String path, final byte[] data, boolean overwrite) throws IOException{
		File dest = new File(path);
		if(dest.exists() && !overwrite){
			throw new IOException("ファイルはすでに存在します。" + path);
		}
		if(!dest.getParentFile().exists())
			dest.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(dest);
		try{
			out.write(data);
		}finally{
			if(out != null)	out.close();
		}
	}
	
	/**ランダムな文字列を返す*/
	public static String random(){
		byte[] rand = new byte[16];
		new SecureRandom().nextBytes(rand);
		String r = DatatypeConverter.printHexBinary(rand);
		String pref = new SimpleDateFormat("yyMMddHHmmssSSS").format(new Date());
		return pref + "-" + r;
	}
	protected static ObjectMapper mapper = new ObjectMapper();
	public static String toString(Object o){
		if(o == null )return "null";
		try{
			return mapper.writeValueAsString(o);
		}catch(Throwable t){return o.toString();}
	}
	/**int配列をマージ**/
	public static int[] join(final int[] arr1, final int[] arr2){
		int[] ret = new int[(arr1 == null ? 0 : arr1.length) + (arr2 == null ? 0 : arr2.length)];
		int pos = 0;
		if(arr1 != null){
			System.arraycopy(arr1, 0, ret, 0, arr1.length);
			pos += arr1.length;
		}
		if(arr2 != null){
			System.arraycopy(arr2,  0,  ret, pos, arr2.length);
		}
		return ret;
	}
	/**配列を連結する*/
	public static String[] concat(final String[] arr1, final String[] arr2){
		String[] ret = new String[(arr1 == null ? 0 : arr1.length) + (arr2 == null ? 0 : arr2.length)];
		int pos = 0;
		if(arr1 != null){
			System.arraycopy(arr1, 0, ret, 0, arr1.length);
			pos += arr1.length;
		}
		if(arr2 != null){
			System.arraycopy(arr2,  0,  ret, pos, arr2.length);
		}
		return ret;
	}
	/**コレクションを区切り文字列に変換*/
	public static String join(final Collection<?> src, final String separator){
		StringBuffer buff = new StringBuffer();
		for(String e : src.toArray(new String[src.size()])){
			if(buff.length()!= 0)buff.append(separator);
			buff.append(e);
		}
		return buff.toString();
	}
	/**int配列に値を追加**/
	public static int[] concat(final int[] arr1, final int val){
		return Util.join(arr1,new int[]{val});
	}
	public static String[] concat(final String[] arr1, final String val){
		return Util.concat(arr1,new String[]{val});
	}
//	public static String arrayToString(Object[] arr){
//		StringBuffer buff = new StringBuffer();
//		for(Object o : arr){
//			if(buff.length() != 0)buff.append(";");
//			buff.append(o.toString());
//		}
//		return "[" + buff.toString() + "]";
//	}
//	
//	public static int[] toIntArray(final String[] args){
//		if(args == null) return new int[0];
//		
//		int[] ret = new int[args.length];
//		for(int c = 0; c < args.length; c ++){
//			String i = args[c];
//			try{
//				if(i.length() == 0)continue;
//				Integer no = Integer.parseInt(i);
//				ret[c] = no.intValue();
//			}catch(Throwable t){
//				System.err.println("failed to parse string as integer." + i);
//				ret[c] = 0;
//			}
//		}
//		return ret;
//	}
//	
	/**リストを重複なしにマージする。Collection.containsで比較し、重複した時は先に出現したほうが優先。*/
	public static <T> Collection<T> merge(Collection<T> arr1, Collection<T> arr2){
		Collection<T> arr = new ArrayList<>();
		if(arr1 != null){
			for(T t : arr1){
				if(!arr.contains(t))
					arr.add(t);
			}
		}
		if(arr2 != null){
			for(T t : arr2){
				if(!arr.contains(t))
					arr.add(t);
			}
		}
		return arr;
	}
	public static <T> Collection<T> merge(final T[] arr1, final T[] arr2){
		return merge(Arrays.asList(arr1), Arrays.asList(arr2));
	}
	/**zipアーカイブを作成する
	 * @param dir 元ファイルの格納先(フルパス)
	 * @param filefilter 対象ファイルのフィルタ
	 * @return 作成したアーカイブのデータ
	 * */
	public static byte[] compress(final String dir, final String filefilter) throws IOException{
		File src = new File(dir);		if(!src.exists() || !src.isDirectory())
			throw new IOException("指定されたディレクトリが存在しません。" + dir);
		String[] files = src.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.matches(filefilter);
			}});
		if(files == null || files.length == 0) throw new IOException("ディレクトリに該当するファイルがありません。");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ZipOutputStream zip = new ZipOutputStream(out);
		for(String cur : files){
			byte[] buff = new byte[1024];
			String path = dir + File.separator + cur;
			ZipEntry ent = new ZipEntry(cur);
			zip.putNextEntry(ent);
			BufferedInputStream is = new BufferedInputStream(new FileInputStream(path));
			for(int read = 0; (read = is.read(buff)) != -1;zip.write(buff, 0, read));
			is.close();
		}
		zip.close();
		out.flush();
		out.close();
		return out.toByteArray();
	}
	/**.jsonファイルをzip圧縮する*/
	public static byte[] compress(final String dir) throws IOException{
		return compress(dir, ".+\\.json");
	}
	/**
	 * zipストリームを展開する
	 * 
	 * @param data zipバイトストリーム
	 * @param path 出力先ディレクトリのフルパス。存在しなければ作成する。
	 * */
	public static void uncompress(final byte[] data, final String path) throws IOException{
		File dir = new File(path);
		if(!dir.exists()){
			dir.mkdirs();
		}
		ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data));
		for(ZipEntry ent = null; (ent = zip.getNextEntry()) != null;){
			String dest = Util.canonicalizePath(path +File.separator +  ent.getName());
			if(ent.isDirectory()){
				new File(ent.getName()).mkdirs();
				continue;
			}
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest));
			byte[] buff = new byte[1024];
			
			for(int read = 0; (read = zip.read(buff)) != -1; ){
				out.write(buff, 0, read);
			}
			out.flush();out.close();
		}
	}
	/***パス名からディレクトリ部分を除いたファイル名を返す*/
	public static String basename(final String path){
		if(path == null || path.length() == 0)return path;
		
		File f = new File(path);
		String ret =f.getName().replace(f.getParent(), "");
		return ret;
		
	}
	
	
	public static byte[] BOM_UTF8 = {(byte)0xef, (byte)0xbb, (byte)0xbf};
	public static byte[] BOM_UTF16BE = {(byte)0xfe,(byte)0xff};
	public static byte[] BOM_UTF16LE = {(byte)0xff, (byte)0xfe};
	public static byte[] BOM_UTF32BE = {(byte)0,(byte)0,(byte)0xfe,(byte)0xff};//未検証
	public static byte[] BOM_UTF32LE = {(byte)0xff,(byte)0xfe,(byte)0,(byte)0};//未検証

	/**テキストファイルを読み込む
	 * 
	 * <ul>
	 * <li>BOMをスキャンして自動変換を試みる
	 * <ul>
	 * <li>UTF8</li>
	 * <li>UTF16BE</li>
	 * <li>UTF16LE</li>
	 * <li>UTF32BE</li>
	 * <li>UTF32LE</li>
	 * </ul>
	 * </li>
	 * <li>BOMで判断できない場合、UTF8かSJISのいずれかと仮定し、バイト列をUTF8として文字列に変換→再度バイト列に戻して、一致したらUTF8、そうでなければSJISと見なす。
	 * */
	public static String loadTextFile(final String path) throws IOException{
		try {
			String encoding = null;
			ByteArrayOutputStream header = new ByteArrayOutputStream();
			FileInputStream in = new FileInputStream(new File(path));
			ByteArrayOutputStream content = new ByteArrayOutputStream();
			for(int i = 0; i < 4; i ++){
				int c = in.read();
				if(c == -1){break;}
				
				header.write(c);
				if(i == BOM_UTF8.length -1 && Arrays.equals(BOM_UTF8, header.toByteArray())){
					encoding = "UTF-8"; break;//BOMは不要→落とす
				}else if(i == BOM_UTF16BE.length -1 && Arrays.equals(BOM_UTF16BE, header.toByteArray())){
					encoding = "UTF-16"; 	//BOMごとデコード
					content.write(header.toByteArray());
					break;
				}else if(i == BOM_UTF16LE.length -1 && Arrays.equals(BOM_UTF16LE, header.toByteArray())){
					encoding = "UTF-16";	//BOMごとデコード
					content.write(header.toByteArray());
					break;
				}else if(i == BOM_UTF32BE.length -1 && Arrays.equals(BOM_UTF32BE, header.toByteArray())){
					encoding = "UTF-32";	 //BOMごとデコード
					content.write(header.toByteArray());
					break;
				}else if(i == BOM_UTF32LE.length -1 && Arrays.equals(BOM_UTF32LE, header.toByteArray())){
					encoding = "UTF-32"; 	//BOMごとデコード
					content.write(header.toByteArray());
					break;
				}
			}
			
			
			
			if(encoding == null){//BOMなし:読み込んだ先頭バイトをロード
				content.write(header.toByteArray());
			}
			for(int c = 0;(c = in.read()) != -1;){
				content.write(c);//最後まで読む
			}
			in.close();
			
			if(encoding == null){//BOMなし:UTF-8かSJISのどちらか
				//UTF-8として文字列>バイト列変換し、可逆ならUTF-8と判断
				//TODO: SJISでもtrueになる
				if(Arrays.equals(content.toByteArray(), 
						new String(content.toByteArray(), "UTF-8").getBytes("UTF-8"))){
					encoding="UTF-8";
				}else{
					encoding = "SJIS";
				}
			}
			String ret = new String(content.toByteArray(), encoding);
	
			return ret;
		}catch(IOException t) {
			throw new IOException("I/O error: "+new File(path).getAbsolutePath() +" " +t.getMessage(), t);
		}
	}

	public static byte[] loadFile(final String path) throws IOException{

		BufferedInputStream in = new BufferedInputStream(new FileInputStream(path));
		ByteArrayOutputStream buff = new ByteArrayOutputStream();
		for(int c = 0;(c = in.read()) != -1;){
			buff.write(c);
		}
		in.close();
		return buff.toByteArray();
	}
//	/**unicodeバイト列を文字列に変換、波ダッシュ問題対策付き*/
//	protected static byte[] convert(final byte[] bytes, final String sourceEncoding, final String targetEncoding){
//		int[][] mapping = {
//			//unicode win
//			{0x301C,0xFF5E},// 全角チルダ
//			{0x2212,0xFF0D},// 全角マイナス
//			{0x00A2,0xFFE0},// セント
//			{0x00A3,0xFFE1},// ポンド
//			{0x00AC,0xFFE2},// ノット
//			{0x2014,0x2015},// 全角マイナスより少し幅のある文字
//			{0x2016,0x2225}// 半角パイプが2つ並んだような文字
//		};
//		try{
//			String str = new String(bytes, sourceEncoding);
//			//byte[] buff = ret.getBytes();
//			byte[] ret = str.getBytes(targetEncoding);
//			
//			return ret;
//		}catch(UnsupportedEncodingException t){return bytes;}//uuum
//	}
	public static void  main(String[] args){
		try{

			BufferedReader reader =new BufferedReader(new InputStreamReader(System.in));
			for(String line = null; (line = reader.readLine()) != null; ) {
				try{
					
					System.out.println(line + " : " + (Util.isAbsolutePath(line) ? "absolute" : "relative"));
					
				}catch(Throwable t){
					t.printStackTrace();
				}
			}
			System.out.println("input path to test ");

		}catch(Throwable t){
			t.printStackTrace();
		}
	}
	enum EncodeMethod{
		MD5,
		SHA1,
	};

	/**パスワードをハッシュしてエンコード
	 * ldappasswdの形式に従うが、とりあえずSHAとMD5のみ対応
{MD5}
{SHA}
	 * 
	 * */
	public static String encodeHash(final String passwd, EncodeMethod method){
		try{
			byte[]  hash= MessageDigest.getInstance(method.toString()).digest(passwd.getBytes("UTF-8"));
			String encoded = Base64.getEncoder().encodeToString(hash);
			return "{" + method + "}" + encoded;
		}catch(UnsupportedEncodingException|NoSuchAlgorithmException | UnsupportedCharsetException t){
			throw new RuntimeException(t);
		}
	}
	/**パスワードをSHA1でハッシュしてエンコード*/
	public static String encodeHash(final String passwd){
		return encodeHash(passwd, EncodeMethod.SHA1);
	}
	/**ハッシュと平文パスワードを比較*/
	public static boolean matchHash(final String encoded, final String val){
		String prefix = "^\\{([A-Za-z0-9]+)\\}(.*)";
		Matcher m = Pattern.compile(prefix).matcher(encoded);
		if(!m.matches())//マッチしなかったら普通の平文と見なす
			return encoded.equals(val);
		String method = m.group(1);
		String value = m.group(2);
		if(EncodeMethod.valueOf(method) == null){
			throw new RuntimeException("エンコード形式を認識できません。"+ encoded);
		}
		byte[] raw = Base64.getDecoder().decode(value);
		try{
			byte[] org = MessageDigest.getInstance(method).digest(val.getBytes("UTF-8"));
			return Arrays.equals(org,  raw);
		}catch(UnsupportedEncodingException |NoSuchAlgorithmException t ){
			throw new RuntimeException(t);
		}
	}
	
	
	
	
//	public static Logger getLogger(String name) {
//		Logger logger = Logger.getLogger(name);
//		return logger;
//	}
//	public static Logger getLogger() {return getLogger(defaultLoggerName);}
//	public static String defaultLoggerName = "workshop";
//
//	public static String getCaller() {
//		StackTraceElement[] st = new Throwable().getStackTrace();
//		for(int i = 0; i < st.length; i ++) {
//			if(st[i].getClassName().equals(Util.class.getName()))
//				continue;
//			return st[i].toString();
//		}
//		return "unknown";
//	}
//	/**次の形式でログを出力
//	 * [enter] 呼び出し元スタック情報 (引数,...)*/
//	public static void enter(Object... param) {
//		info("enter:", param);
//	}
//	public static void enter() {
//		info("enter:");
//	}
//	public static void info(String msg) {
//		info(msg, new Object[0]);
//	}
//	public static void info(String msg, Object... params) {
//		String str = String.format("%s %s (%s)", msg, getCaller(), join(params, ", "));
//		getLogger().info(str);
//	}
//	public static void debug(String msg, Object...params) {
//		
//		String paramstr = join(params, ",");
//	getLogger().debug(getCaller()  + "(" + paramstr + ") : " + msg);
//	}
//	public static void debug(String msg) {
//		debug(msg, new Object[0]);
//	}
//
//	public static void trace(String msg, Object...params) {
//
//		String paramstr = join(params, ",");
//		getLogger().info(getCaller() + "(" + paramstr + ") : " + msg);
//		
//	}
//	public static void trace(String msg) {
//		trace(msg, new Object[0]);
//	}
//	public static void warn(String msg) {
//		getLogger().error(getCaller() + ": " + msg);
//	}
//	public static void error(String msg, Throwable t) {
//		getLogger().error(getCaller() + ": " + msg);
//		if(t != null)	t.printStackTrace(System.err);
//	}
//	public static void error(String msg) {
//		error(msg, null);
//	}
	/**配列を文字列化して返す*/
	public static String join(Object[] arr, String sep) {
		StringBuffer buff = new StringBuffer();
		if(arr != null) {for(Object o : arr){
			if(buff.length() != 0) buff.append(sep);
			buff.append(o == null ? "" : o.toString());
		}}
		return buff.toString();
	}
//	@Singleton static ObjectMapper mapper = null;
	static {
		mapper = new ObjectMapper();
        mapper.setDateFormat(new SimpleDateFormat("HH:mm:ss"));

	}
	public static ObjectMapper getObjectMapper() {
		return mapper;
	}
	public static String toJson(Object o) {
		try {
			if(o == null)return "";						
			return mapper.writeValueAsString(o);
		}catch (JacksonException e) {
			ServiceLogger.getLogger().error("failed to map object to json", e);
			return "[JSON ERROR]" + o.toString();
		}
	}
	public static boolean isAbsolutePath(String path) {
		boolean ret = Path.of(path).isAbsolute();
		return ret;

	}
}

