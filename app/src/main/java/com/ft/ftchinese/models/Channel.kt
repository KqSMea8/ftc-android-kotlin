package com.ft.ftchinese.models

import android.net.Uri
import org.jetbrains.anko.AnkoLogger
import java.util.*

/**
 * The following data keys is used to parse JSON data passed from WebView by ContentWebViewInterface.postItems methods.
 * Most of them are useless, serving as placeholders so that we can extract the deep nested JSON values.
 * `ChannelMeta` represent the `meta` fieled in JSON data.
 */
data class ChannelContent(
        val meta: ChannelMeta,
        val sections: Array<ChannelSection>
)

data class ChannelMeta(
        val title: String,
        val description: String,
        val theme: String,
        val adid: String,
        val adZone: String = "home"
)


data class ChannelSection(
        val type: String,
        val title: String,
        val name: String,
        val side: String,
        val sideAlign: String,
        val lists: Array<ChannelList>
)

data class ChannelList(
        val name: String,
        val title: String,
        val preferLead: String,
        val sponsorAdId: String,
        val items: Array<ChannelItem>
)
/**
 * ChannelItem represents an item in a page of ViewPager.
 * This is the data tier passed to AbsContentActivity so that it know what kind of data to load.
 * iOS equivalent might be Page/Layouts/Content/ContentItem.swift#ContentItem
 * The fields are collected from all HTML elements `div.item-container-app`.
 * See https://github.com/FTChinese/android-client/app/scripts/list.js.
 *
 * In short it used those attributes:
 * `data-id` for `id`
 * `data-type` for tier. Possible values: `story`, `interactive`,
 * The content of `a.item-headline-link` inside `div.item-container-app` for `headline`
 * `data-audio` for `shortlead`
 * `data-caudio` for `caudio`
 * `data-eaudio` for `eaudio`
 * `data-sub-tier` for `subType`. Possible values: `radio`, `speedreading`
 * `data-date` for `timeStamp`
 *
 * The fields in ChannelItem are also persisted to SQLite when user clicked on it.
 * It seems the Room library does not work well with Kotlin data class. Use a plain class works.
 *
 * This class is also used to record reading history. `standfirst` is used only for this purpose. `subType` and `shortlead` should not be used for this purpose. ArticleStore could only recored `type==story`.
 */
data class ChannelItem(
        val id: String,
        // For column type, you should start a ChannelActivity instead of  StoryActivity.
        val type: String, // story | premium | video | interactive | column |
        val subType: String? = null, // speedreading | radio
        var headline: String, // It seems it is not used. It will later be changed to JSON data's cheadline field
        val eaudio: String? = null,
        val shortlead: String? = null, // this is a url of mp3 for subType radio.
        val timeStamp: String? = null // "1536249600"
) : AnkoLogger {

    var keywords: String? = null
    // These two properties are not parsed from JSON.
    // They are copy from ChannelMeta
    var channelTitle: String = ""
    var theme: String = "default"
    var adId: String = ""
    var adZone: String = ""
    var hideAd: Boolean = false

    // For a story, this is extracted from json
    // retrieved from server;
    // For other types of article, we have no way to get the value.
    var standfirst: String? = null

    // Used for sharing
    val canonicalUrl: String
        get() = "$URL_FTC/$type/$id"

    private val isSevenDaysOld: Boolean
        get() {
            if (timeStamp == null) {
                return false
            }

            val sevenDaysLater = Date((timeStamp.toLong() + 7 * 24 * 60 * 60) * 1000)
            val now = Date()

            if (sevenDaysLater.after(now)) {
                return false
            }

            return true
        }

    val isMembershipRequired: Boolean
        get() = isSevenDaysOld ||
                type == TYPE_PREMIUM ||
                (type == TYPE_INTERACTIVE && subType == SUB_TYPE_RADIO) ||
                (type == TYPE_INTERACTIVE && subType == SUB_TYPE_SPEED_READING)

    // File name used to cache/retrieve json data.
    val cacheFileName: String
        get() = "${type}_$id.json"

    val cacheHTMLName: String
        get() = "${type}_$id.html"

    private val commentsId: String
        get() = when(subType) {
                "interactive" -> "r_interactive_$id"
                "video" -> "r_video_$id"
                "story" -> id
                "photo", "photonews" -> "r_photo_$id"
                else -> "r_${type}_$id"
            }


    private val commentsOrder: String
        get() {
            return "story"
        }

    /**
     * URL used to fetch an article
     * See Page/FTChinese/Main/APIs.swift
     * https://api003.ftmailbox.com/interactive/12339?bodyonly=no&webview=ftcapp&001&exclusive&hideheader=yes&ad=no&inNavigation=yes&for=audio&enableScript=yes&v=24
     */
    val apiUrl: String
        get() = when(type) {
            TYPE_STORY, TYPE_PREMIUM -> "$URL_MAILBOX/index.php/jsapi/get_story_more_info/$id"

            TYPE_COLUMN -> "$URL_MAILBOX/$type/$id?bodyonly=yes&webview=ftcapp&bodyonly=yes"

            TYPE_INTERACTIVE -> when (subType) {
                //"https://api003.ftmailbox.com/$type/$id?bodyonly=no&exclusive&hideheader=yes&ad=no&inNavigation=yes&for=audio&enableScript=yes&showAudioHTML=yes"
                SUB_TYPE_RADIO -> "$URL_MAILBOX/$type/$id?bodyonly=yes&webview=ftcapp&i=3&001&exclusive"
                SUB_TYPE_SPEED_READING -> "$URL_FTC/$type/$id?bodyonly=yes&webview=ftcapp&i=3&001&exclusive"

                SUB_TYPE_MBAGYM -> "$URL_FTC/$type/$id"

                else -> "$URL_MAILBOX/$type/$id?bodyonly=no&webview=ftcapp&001&exclusive&hideheader=yes&ad=no&inNavigation=yes&for=audio&enableScript=yes&v=24"
            }

            TYPE_VIDEO -> "$URL_FTC/$type/$id?bodyonly=yes&webview=ftcapp&004"

            else -> "$URL_MAILBOX/$type/$id?webview=ftcapp"
        }

    private fun pickAdZone(homepageZone: String, fallbackZone: String): String {
        if (!keywords.isNullOrBlank()) {
            return fallbackZone
        }

        for (sponsor in SponsorManager.sponsors) {
            if ((keywords?.contains(sponsor.tag) == true || keywords?.contains(sponsor.title) == true) && sponsor.zone.isNotEmpty() ) {
                return if (sponsor.zone.contains("/")) {
                    sponsor.zone
                } else {
                    "home/special/${sponsor.zone}"
                }
            }
        }

        if (adZone != homepageZone) {
            return adZone
        }

        if (keywords?.contains("lifestyle") == true) {
            return "lifestyle"
        }

        if (keywords?.contains("management") == true) {
            return "management"
        }

        if (keywords?.contains("opinion") == true) {
            return "opinion"
        }

        if (keywords?.contains("创新经济") == true) {
            return "创新经济"
        }

        if (keywords?.contains("markets") == true) {
            return "markets"
        }

        if (keywords?.contains("economy") == true) {
            return "economy"
        }

        if (keywords?.contains("china") == true) {
            return "china"
        }

        return fallbackZone
    }

    private fun pickAdchID(homepageId: String, fallbackId: String): String {
        if (!keywords.isNullOrBlank()) {
            for (sponsor in SponsorManager.sponsors) {
                if ((keywords?.contains(sponsor.tag) == true || keywords?.contains(sponsor.title) == true) && sponsor.adid.isNotEmpty()) {
                    return sponsor.adid
                }
            }

            if (adId != homepageId) {
                return adId
            }

            if (keywords?.contains("lifestyle") == true) {
                return "1800"
            }

            if (keywords?.contains("management") == true) {
                return "1700"
            }

            if (keywords?.contains("opinion") == true) {
                return "1600"
            }

            if (keywords?.contains("创新经济") == true) {
                return "2100"
            }

            if (keywords?.contains("markets") == true) {
                return "1400"
            }

            if (keywords?.contains("economy") == true) {
                return "1300"
            }

            if (keywords?.contains("china") == true) {
                return "1100"
            }

            return "1200"
        }

        if (adId.isNotEmpty()) {
            return fallbackId
        }
        return fallbackId
    }

    fun renderStory(template: String?, story: Story?, language: Int, follows: JSFollows): String? {

        if (template == null || story == null) {

            return null
        }

        standfirst = story.clongleadbody
        keywords = story.keywords

        var shouldHideAd = false

        if (hideAd) {
            shouldHideAd = true
        } else if (!keywords.isNullOrBlank()) {
            if (keywords?.contains(Keywords.removeAd) == true) {
                shouldHideAd = true
            } else {
                for (sponsor in SponsorManager.sponsors) {
                    if (keywords?.contains(sponsor.tag) == true || keywords?.contains(sponsor.title) == true) {
                        shouldHideAd = sponsor.hideAd == "yes"
                        break
                    }
                }
            }
        }


        val adMPU = if (shouldHideAd) "" else AdParser.getAdCode(AdPosition.MIDDLE_ONE)

        var body = ""
        var title = ""

        when (language) {
            LANGUAGE_CN -> {
                body = story.getCnBody(withAd = !shouldHideAd)
                title = story.title.cn
            }
            LANGUAGE_EN -> {
                body = story.getEnBody(withAd = !shouldHideAd)
                title = story.title.en ?: ""
            }
            LANGUAGE_BI -> {
                body = story.bodyAlignedXML
                title = "${story.title.cn}<br>${story.title.en}"
            }
        }

        val storyHTMLOriginal = template
                .replace("{story-tag}", story.tag)
                .replace("{story-author}", story.cauthor)
                .replace("{story-genre}", story.genre)
                .replace("{story-area}", story.area)
                .replace("{story-industry}", story.industry)
                .replace("{story-main-topic}", "")
                .replace("{story-sub-topic}", "")
                .replace("{adchID}", pickAdchID(HOME_AD_CH_ID, DEFAULT_STORY_AD_CH_ID))
                .replace("{comments-id}", commentsId)
                .replace("{story-theme}", story.htmlForTheme())
                .replace("{story-headline}", title)
                .replace("{story-lead}", story.standfirst)
                .replace("{story-image}", story.htmlForCoverImage())
                .replace("{story-time}", story.createdAt)
                .replace("{story-byline}", story.byline)
                .replace("{story-body}", body)
                .replace("{story-id}", story.id)
                .replace("{related-stories}", story.htmlForRelatedStories())
                .replace("{related-topics}", story.htmlForRelatedTopics())
                .replace("{comments-order}", commentsOrder)
                .replace("{story-container-style}", "")
                .replace("'{follow-tags}'", follows.tag)
                .replace("'{follow-topic}'", follows.topic)
                .replace("'{follow-industry}'", follows.industry)
                .replace("'{follow-area}'", follows.area)
                .replace("'{follow-augthor}'", follows.author)
                .replace("'{follow-column}'", follows.column)
                .replace("{ad-zone}", pickAdZone(HOME_AD_ZONE, DEFAULT_STORY_AD_ZONE))
                .replace("{ad-mpu}", adMPU)
                //                        .replace("{font-class}", "")
                .replace("{{googletagservices-js}}", JSCodes.googletagservices)

        val storyHTML = AdParser.updateAdCode(storyHTMLOriginal, shouldHideAd)

        val storyHTMLCheckingVideo = JSCodes.getInlineVideo(storyHTML)

        return JSCodes.getCleanHTML(storyHTMLCheckingVideo)

    }

    companion object {
        private const val PREF_NAME_FAVOURITE = "favourite"

        const val LANGUAGE_CN = 0
        const val LANGUAGE_EN = 1
        const val LANGUAGE_BI = 2
        const val TYPE_STORY = "story"
        const val TYPE_PREMIUM = "premium"
        const val TYPE_VIDEO = "video"
        const val TYPE_INTERACTIVE = "interactive"
        const val TYPE_COLUMN = "column"
        const val TYPE_PHOTO_NEWS = "photonews"
        const val TYPE_CHANNEL = "channel"
        const val TYPE_TAG = "tag"
        const val TYPE_M = "m"

        const val SUB_TYPE_RADIO = "radio"
        const val SUB_TYPE_USER_COMMENT = ""
        const val SUB_TYPE_MBAGYM = "mbagym"
        const val SUB_TYPE_SPEED_READING = "speedreading"

        const val SUB_TYPE_CORP = "corp"
        const val SUB_TYPE_MARKETING = "marketing"

        const val HOME_AD_ZONE = "home"
        const val DEFAULT_STORY_AD_ZONE = "world"

        const val HOME_AD_CH_ID = "1000"
        const val DEFAULT_STORY_AD_CH_ID = "1200"
    }
}

