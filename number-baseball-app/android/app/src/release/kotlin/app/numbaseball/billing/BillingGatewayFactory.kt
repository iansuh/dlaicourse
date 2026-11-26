package app.numbaseball.billing

import android.content.Context

/** Release builds always use Google Play Billing. (Debug builds use a demo gateway, see src/debug.) */
fun createBillingGateway(context: Context): BillingGateway = PlayBillingGateway(context)
