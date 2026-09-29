package com.ibis.expense.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ibis.expense.R

object PaymentMethod {
    const val WECHAT = "微信"
    const val ALIPAY = "支付宝"
    val ALL_WITH_LEGACY = setOf("", WECHAT, ALIPAY)
}

@Composable
fun PaymentIcon(method: String) {
    val icon = if (method == PaymentMethod.WECHAT) R.drawable.ic_wechat else R.drawable.ic_alipay
    Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
}
