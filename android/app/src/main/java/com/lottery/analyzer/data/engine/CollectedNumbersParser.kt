package com.lottery.analyzer.data.engine

import com.lottery.analyzer.data.model.*

/** Pure collector parser. Ambiguous or invalid rows are reported, never partially accepted. */
object CollectedNumbersParser {
    const val VERSION="collected-v2"
    private val brackets=Regex("""【[^】]*】|\[[^\]]*\]|\([^)]*\)|（[^）]*）""")
    private val prefix=Regex("""^(?:(?:他人)?第?\s*\d+\s*注[:：\s]*|注\s*\d+[:：\s]*|\d+[.、)]\s+)""")
    private val redLabel=Regex("""(?:红球|前区|红)[:：]?""")
    private val blueLabel=Regex("""(?:蓝球|后区|蓝)[:：]?""")
    fun parse(text:String,type:LotteryType,check:()->Unit={}):FrequencyAnalysisResult {
        require(CollectionInputPolicy.error(text)==null){CollectionInputPolicy.error(text)!!}
        val groups=mutableListOf<Pair<List<Int>,List<Int>>>()
        val issues=mutableListOf<String>();var invalid=0
        fun segment(value:String,max:Int):List<Int> {
            val tokens=value.trim().split(Regex("[^0-9]+")).filter{it.isNotBlank()}
            val all=mutableListOf<Int>()
            for(token in tokens) {
                check()
                require(token.length<=2||token.length%2==0){"连写数字长度为奇数，不能确定号码边界"}
                val parts=if(token.length<=2)listOf(token) else token.chunked(2)
                for(part in parts) {
                    val n=requireNotNull(part.toIntOrNull()){"号码不是整数"}
                    require(n in 1..max){"号码超出范围1~$max"}
                    require(n !in all){"同一行号码重复，请核对或拆为多行"}
                    all+=n
                }
            }
            return all.sorted()
        }
        for((index,line)in text.lineSequence().withIndex()) {
            check();if(line.isBlank())continue
            try {
                val cleaned=line.replace(brackets," ").trim().replaceFirst(prefix,"").replace(redLabel,"").replace(blueLabel,"+").trim()
                require(!Regex("""-\s*\d|\d[.．]\d""").containsMatchIn(cleaned)){"不支持负数、小数或范围，请用空格列出整数号码"}
                require(type.maxBlueNumber>0||'+' !in cleaned){"快乐8无蓝球，请使用空格分隔整行号码，不能使用加号"}
                val parts=cleaned.split('+');require(parts.size<=2){"一行只能有一个红蓝球分隔符"}
                val reds=segment(parts[0],type.maxRedNumber)
                val blues=if(parts.size==2)segment(parts[1],type.maxBlueNumber) else emptyList()
                require(reds.isNotEmpty()||blues.isNotEmpty()){"未识别到号码"}
                groups+=reds to blues
            } catch(e:IllegalArgumentException) {
                invalid++;if(issues.size<10)issues+="第${index+1}行：${e.message}"
            }
        }
        val reds=groups.flatMap{it.first}.groupingBy{it}.eachCount()
        val blues=groups.flatMap{it.second}.groupingBy{it}.eachCount()
        return FrequencyAnalysisResult(groups.size,reds,reds.toList().sortedWith(compareByDescending<Pair<Int,Int>>{it.second}.thenBy{it.first}),
            groups.map{it.first}.filter{it.isNotEmpty()},blues,blues.toList().sortedWith(compareByDescending<Pair<Int,Int>>{it.second}.thenBy{it.first}),groups,issues,invalid,VERSION)
    }
}
