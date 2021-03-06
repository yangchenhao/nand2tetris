package org.reion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * 从输入流中删除所有注释和空格，并根据Jack语法 规则将输入流分解成Jack语言的字元.
 * 
 * @author Reion
 * 
 */
public class JackTokenizer {

	/**
	 * 关键字字符串数组
	 */
	public static final String[] KEYWORDS = {"class", "constructor",
			"function", "method", "field", "static", "var", "int", "char",
			"boolean", "void", "true", "false", "null", "this", "let", "do",
			"if", "else", "while", "return"};

	/**
	 * 符号字符串数组
	 */
	public static final String[] SYMBOLS = {"{", "}", "(", ")", "[", "]", ".",
			",", ";", "+", "-", "*", "/", "&", "|", "<", ">", "=", "~"};
	/**
	 * 最小整数
	 */
	public static final int MIN_INT = 0;

	/**
	 * 最大整数
	 */
	public static final int MAX_INT = 0x7FFF;
	
	/**
	 * 不可见字符用于提取字符串用.
	 */
	public static final String BEL = String.valueOf((char)7);

	/**
	 * 标示符正则式
	 */
	public static final String IDENTIFIER = "^[a-zA-Z_]{1}[a-zA-Z0-9_]*";

	/**
	 * 行末注释
	 */
	public static final String SINGLELINE_COMMENT = "//";

	/**
	 * 多行注释开始
	 */
	public static final String MULT_START_COMMENT = "/*";

	/**
	 * API注释开始
	 */
	public static final String MULT_API_START_COMMENT = "/**";

	/**
	 * 多行注释结束
	 */
	public static final String MULT_END_COMMENT = "*/";

	/**
	 * 字元类型.
	 */
	public static enum TokenType {
		KEYWORD, SYMBOL, IDENTIFIER, INT_CONST, STRING_CONST;
		@Override
		public String toString() {
			String name = this.name().toLowerCase();
			if (INT_CONST.equals(this)) {
				name = "integerConstant";
			} else if (STRING_CONST.equals(this)) {
				name = "stringConstant";
			}
			return name;
		}
	}

	/**
	 * JACK关键字
	 */
	public static HashSet<String> keywordSet;

	/**
	 * JACK符号
	 */
	public static HashSet<String> symbolSet;

	// 源文件输入流.
	// private FileReader fReader;
	// 源文件.
	private File jFile;
	// 当前字元
	private String curToken;
	// 当前字元类型
	private TokenType tokenType;
	// 总代码行数
	private int totalNum = -1;
	// 当前行号
	private int curLineNum = 0;
	// 去除注释及多余空行后的纯代码序列
	private Map<Integer, String> jSeqs = new LinkedHashMap<Integer, String>();
	// 当前行的字元链表
	private LinkedList<String> tokensOfLine;
	// 当前行的索引
	private int curIndex = 0;
	// 当前行的最大索引
	private int curMaxIndex = -1;

	// 静态初始化
	static {
		// 关键字集合
		keywordSet = new HashSet<String>();
		for (String str1 : KEYWORDS) {
			keywordSet.add(str1);
		}
		// 符号集合
		symbolSet = new HashSet<String>();
		for (String str2 : SYMBOLS) {
			symbolSet.add(str2);
		}
	}

	/**
	 * 构造方法.
	 * 
	 * @param file
	 *            jack file
	 * @param fr
	 *            file reader
	 */
	public JackTokenizer(final File file, final FileReader fr) {
		jFile = file;
		FileReader fReader = fr;
		init(fReader);
		try {
			fReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// 初始化构建纯代码行
	private void init(FileReader fReader) {
		BufferedReader br = new BufferedReader(fReader);
		// 将源文件提取出纯汇编命令行序列
		String curStr = null, tmp = null;
		StringBuffer strB = null;
		int multTagCounter = 0;
		int lineNum = 0;
		try {
			while ((curStr = br.readLine()) != null) {
				curStr = curStr.trim();
				// 空行、单行注释及多行注释内跳过
				if (curStr.startsWith(SINGLELINE_COMMENT)
						|| curStr.length() < 1
						|| (multTagCounter > 0 && curStr
								.indexOf(MULT_END_COMMENT) < 0)) {
					continue;
				}
				// 去除行末注释
				if (curStr.indexOf(SINGLELINE_COMMENT) > 0) {
					curStr = curStr.substring(0,
							curStr.indexOf(SINGLELINE_COMMENT));
				}

				// 处理多行及API注释
				int msc = curStr.indexOf(MULT_START_COMMENT);
				int masc = curStr.indexOf(MULT_API_START_COMMENT);
				int mec = curStr.indexOf(MULT_END_COMMENT);
				if ((msc >= 0 || masc >= 0) && mec >= 0) {
					if (msc >= 0 && masc < 0) {
						curStr = curStr.substring(0, msc)
								+ curStr.substring(mec + 2, curStr.length());
					}
					if (masc >= 0) {
						curStr = curStr.substring(0, masc)
								+ curStr.substring(mec + 2, curStr.length());
					}
				} else {
					if (msc >= 0 && masc < 0) {
						multTagCounter++;
						if (msc > 0) {
							curStr = curStr.substring(0, msc);
						} else {
							continue;
						}
					}
					if (masc >= 0) {
						multTagCounter++;
						if (masc > 0) {
							curStr = curStr.substring(0, masc);
						} else {
							continue;
						}
					}
					if (mec >= 0) {
						multTagCounter--;
						curStr = curStr.substring(mec + 2, curStr.length());
					}
				}
				// 将字元用空格进行分隔 [字符串除空格替换成BEL外保持原样]
				int start = 0;
				strB = new StringBuffer();
				for (char c : curStr.toCharArray()) {
					tmp = String.valueOf(c);
					if (tmp.matches("\"")) {
						start++;
					}
					if (start==0 || start%2==0) {
						if (symbolSet.contains(tmp)) {
							tmp = " " +tmp+ " ";
						}
					} else {
						if (" ".equals(tmp)) {
							tmp = BEL;
						}
					}
					strB.append(tmp);
				}
	
				// 去除多余空格
				curStr = strB.toString().replaceAll("\\s+", " ").trim();
				
				if (curStr.length() < 1) {
					continue;
				}
				jSeqs.put(++lineNum, curStr);
			}
			totalNum = jSeqs.size();
			curLineNum = 1;
			tokensOfLine = new LinkedList<String>(Arrays.asList(jSeqs.get(
					curLineNum).split(" ")));
			curIndex = 0;
			curMaxIndex = tokensOfLine.size() - 1;
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 输入中是否还有字元.
	 * 
	 * @return boolean value
	 */
	public boolean hasMoreTokens() {
		// 当前行小于总行数 或 当前为最后行且字元索引小于等于最大索引
		if (curLineNum < totalNum
				|| (curLineNum == totalNum && curIndex - 1 < curMaxIndex)) {
			return true;
		}
		return false;
	}

	/**
	 * 从当前输入中读取下一个字元，使其成为当前字元.
	 */
	public void advance() {
		if (!hasMoreTokens()) {
			throw new RuntimeException(
					"Current index is already at the end of the token array!");
		}

		if (curIndex <= curMaxIndex) {
			curToken = tokensOfLine.get(curIndex++);
			tokenType = tokenType();
			curMaxIndex = tokensOfLine.size() - 1;
		} else if (curLineNum < totalNum) {
			tokensOfLine = new LinkedList<String>(Arrays.asList(jSeqs.get(
					++curLineNum).split(" ")));
			curIndex = 0;
			curMaxIndex = tokensOfLine.size() - 1;
			advance();
		}
	}

	/**
	 * 从当前输入中后退到上一个字元，使其成为当前字元.
	 */
	public void recede() {
		// 当前行够回退
		if (curIndex > 1) {
			curIndex -= 2;
			curToken = tokensOfLine.get(curIndex++);
			tokenType = tokenType();
		} else if (curLineNum > 1) { // 非首行回退上一行
			tokensOfLine = new LinkedList<String>(Arrays.asList(jSeqs.get(
					--curLineNum).split(" ")));
			curMaxIndex = tokensOfLine.size() - 1;
			curIndex = curMaxIndex;
			curToken = tokensOfLine.get(curIndex++);
			tokenType = tokenType();
		} else {
			throw new RuntimeException(
					"Current index is already at the begin of the token array!");
		}
	}

	/**
	 * 返回当前字元的类型.
	 * 
	 * @return
	 */
	private TokenType tokenType() {
		if (keywordSet.contains(curToken)) {
			return TokenType.KEYWORD;
		} else if (symbolSet.contains(curToken)) {
			return TokenType.SYMBOL;
		} else if (curToken.matches("\\d+")) {
			int cst = Integer.parseInt(curToken);
			if (cst < MIN_INT || cst > MAX_INT) {
				throw new RuntimeException(
						"int constant is out of range at line " + curLineNum);
			}
			return TokenType.INT_CONST;
		} else if (curToken.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
			return TokenType.IDENTIFIER;
		} else if (curToken.matches("^\"[^\"\n]*\"$")) {
			return TokenType.STRING_CONST;
		} else {
			throw new RuntimeException("Unknow token type!" + "\n" + getCurrentInfo());
		}
	}

	/**
	 * 返回当前字元的关键字，仅当TokenType为KEYWORD.
	 * 
	 * @return
	 */
	private String keyword() {
		return curToken;
	}

	/**
	 * 返回当前字元的字符，仅当tokenType()返回值是SYMBOL调用
	 * 
	 * @return symbol
	 */
	private String symbol() {
		return curToken;
	}

	/**
	 * 返回当前字元的字符，仅当tokenType()返回值是IDENTIFIER调用
	 * 
	 * @return identifier
	 */
	private String identifier() {
		return curToken;
	}

	/**
	 * 返回当前字元的值，仅当tokenType()返回值是INT_CONST调用
	 * 
	 * @return int
	 */
	private int intVal() {
		return Integer.parseInt(curToken);
	}

	/**
	 * 返回当前字元的字符，仅当tokenType()返回值是STRING_CONST调用
	 * 
	 * @return string
	 */
	private String stringVal() {
		return curToken.replaceAll(BEL," ").substring(1, curToken.length()-1);
	}

	/**
	 * 获取输入文件.
	 * 
	 * @return jack File
	 */
	public File getJFile() {
		return jFile;
	}

	/**
	 * 获得字元值.
	 * 
	 * @param type
	 *            校验类型
	 * @return 字元值
	 */
	public Object getTokenValue(TokenType type) {
		if (type != null && !type.equals(tokenType)) {
			throw new RuntimeException("Need TokenType '" + type.name()
					+ "' but current is '" + tokenType.name() + "'" + "\n" + getCurrentInfo());
		}
		Object obj = null;
		if (TokenType.KEYWORD.equals(tokenType)) {
			obj = keyword();
		} else if (TokenType.IDENTIFIER.equals(tokenType)) {
			obj = identifier();
		} else if (TokenType.INT_CONST.equals(tokenType)) {
			obj = intVal();
		} else if (TokenType.STRING_CONST.equals(tokenType)) {
			obj = stringVal();
		} else if (TokenType.SYMBOL.equals(tokenType)) {
			obj = symbol();
		}
		return obj;
	}

	/**
	 * 获取当前字元的类型.
	 * 
	 * @return TokenType
	 */
	public TokenType getTokenType() {
		return tokenType;
	}

	/**
	 * 获取当前处理字元信息.
	 * 
	 * @return
	 */
	public String getCurrentInfo() {
		return "Line " + curLineNum + ", Current Token '" + curToken
				+ "', Token Index '" + (curIndex - 1) + "', Code '"
				+ jSeqs.get(curLineNum) + "'";
	}

}
