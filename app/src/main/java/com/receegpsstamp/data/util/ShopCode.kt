package com.receegpsstamp.data.util

/** 2-letter uppercase code from a name: "Pepsi" -> "PE", "Mehta Agency" -> "ME". */
fun code2(name: String): String {
    val letters = name.filter { it.isLetter() }.uppercase()
    return when {
        letters.length >= 2 -> letters.take(2)
        letters.length == 1 -> letters + "X"
        else -> "XX"
    }
}
