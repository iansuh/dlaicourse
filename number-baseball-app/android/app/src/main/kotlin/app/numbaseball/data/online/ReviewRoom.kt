package app.numbaseball.data.online

import app.numbaseball.core.protocol.RoomView

/**
 * The server's review-room bot account (protocol §8.5, `Hub.BOT_ID`). The protocol has no "review room" flag, so the
 * client recognises the room by its bot opponent; used to keep the Play reviewers' path free of interstitials and of the
 * "Remove ads" link (POLICY_CHECKLIST 11.1 safeguard 4).
 */
const val REVIEW_BOT_ID = "reviewbot0001"

val RoomView.isReviewRoom: Boolean get() = players.any { it.id == REVIEW_BOT_ID }
