package app.sigmatube.data.auth

data class YtComment(
    val id: String,
    val author: String,
    val authorAvatar: String?,
    val authorChannelId: String?,
    val isCreator: Boolean,
    val isVerified: Boolean,
    val text: String,
    val publishedText: String,
    val likeCountText: String?,
    val replyCountText: String?,
    val repliesToken: String?,
    val isHearted: Boolean,
    val isPinned: Boolean,
    val likeState: LikeStatus,
    val likeAction: String?,
    val unlikeAction: String?,
    val dislikeAction: String?,
    val undislikeAction: String?,
)

class YtCommentsPage(
    val comments: List<YtComment>,
    val nextToken: String?,
    val sorts: List<SortChip>,
    val countText: String?,
    val disabled: Boolean,
)
