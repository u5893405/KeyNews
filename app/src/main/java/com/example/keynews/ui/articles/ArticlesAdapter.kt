package com.example.keynews.ui.articles

// CodeCleaner_Start_999e9ecc-6ad8-4135-a7f8-825d62888048
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.keynews.data.model.NewsArticle
import com.example.keynews.databinding.ItemArticleBinding
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import com.example.keynews.R
import com.example.keynews.data.model.NewsSource
import com.example.keynews.util.TimeUtil

class ArticlesAdapter(
    private val onArticleClick: (NewsArticle) -> Unit,
    private val onCheckRead: (NewsArticle) -> Unit,
    private val onSourceClick: (NewsArticle) -> Unit,
    private val onBookmarkClick: (NewsArticle) -> Unit = {} // Optional callback for bookmark clicks
) : RecyclerView.Adapter<ArticlesAdapter.ViewHolder>() {

    private val articles = mutableListOf<NewsArticle>()
    private val sourcesMap = mutableMapOf<Long, NewsSource>()
    private val matchedKeywordsMap = mutableMapOf<String, List<String>>() // Article link -> matched keywords

    private var currentlyReadingLink: String? = null
    private var articleBodyLengthLimit: Int = 120 // Default value
    
    // Orange color with some transparency for highlighting matched keywords
    private val KEYWORD_HIGHLIGHT_COLOR = Color.parseColor("#FFA726")
    private val READING_HIGHLIGHT_COLOR = Color.YELLOW
    
    // Calculate how many lines to display based on character limit
    private fun calculateMaxLines(charLimit: Int): Int {
        // Rough estimate: average of ~40 chars per line at normal width
        // Adjust this formula as needed for your specific layout
        return maxOf(1, minOf(10, (charLimit / 40) + 1))
    }

    fun submitList(list: List<NewsArticle>) {
        articles.clear()
        articles.addAll(list)
        notifyDataSetChanged()
    }

    fun updateSources(sources: List<NewsSource>) {
        sourcesMap.clear()
        for (source in sources) {
            sourcesMap[source.id] = source
        }
        notifyDataSetChanged()
    }
    
    fun updateMatchedKeywords(matches: Map<String, List<String>>) {
        matchedKeywordsMap.clear()
        matchedKeywordsMap.putAll(matches)
        notifyDataSetChanged()
    }
    
    fun setArticleBodyLengthLimit(limit: Int) {
        articleBodyLengthLimit = limit
        notifyDataSetChanged()
    }

    fun setCurrentlyReadingLink(link: String?) {
        currentlyReadingLink = link
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(articles[position])
    }

    override fun getItemCount(): Int = articles.size

    inner class ViewHolder(private val binding: ItemArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(article: NewsArticle) {
            // Highlight if this article is currently being read
            if (article.link == currentlyReadingLink) {
                binding.root.setBackgroundColor(READING_HIGHLIGHT_COLOR)
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
            }

            // Checkbox reflects read/unread state
            binding.checkboxRead.isChecked = article.isRead

            // Get matched keywords for this article
            val matchedKeywords = matchedKeywordsMap[article.link] ?: emptyList()
            
            // Apply title with possible keyword highlighting
            if (matchedKeywords.isNotEmpty()) {
                binding.tvTitle.text = highlightKeywords(article.title, matchedKeywords)
            } else {
                binding.tvTitle.text = article.title
            }
            
            // Calculate max lines based on character limit
            val maxLines = calculateMaxLines(articleBodyLengthLimit)
            binding.tvBody.maxLines = maxLines
            
            // Apply body with length limit and possible keyword highlighting
            val description = article.description ?: ""
            val limitedDescription = if (description.length > articleBodyLengthLimit) {
                description.substring(0, articleBodyLengthLimit) + "..."
            } else {
                description
            }
            
            if (matchedKeywords.isNotEmpty()) {
                binding.tvBody.text = highlightKeywords(limitedDescription, matchedKeywords)
            } else {
                binding.tvBody.text = limitedDescription
            }

            // Get the source name or format the URL
            val source = sourcesMap[article.sourceId]
            val sourceText = if (source != null) {
                if (source.name.isNotBlank()) {
                    source.name
                } else {
                    formatUrl(source.rssUrl)
                }
            } else {
                "Unknown source"
            }
            
            // Set source name in top row of right column
            binding.tvSourceTime.text = sourceText
            
            // Make source name clickable - opens article in browser
            binding.tvSourceTime.setOnClickListener {
                onSourceClick(article)
            }
            
            // Set relative time in bottom row
            binding.tvTime.text = getRelativeTimeSpan(article.publishDateUtc)

            // Clicking the title reads the article with TTS
            binding.tvTitle.setOnClickListener {
                onArticleClick(article)
            }
            
            // Clicking checkbox toggles read/unread
            binding.checkboxRead.setOnClickListener {
                onCheckRead(article)
            }
            
            // Set up bookmark icon
            if (article.isReadLater) {
                binding.ivBookmark.setImageResource(R.drawable.bookmark_added)
            } else {
                binding.ivBookmark.setImageResource(R.drawable.bookmark_add)
            }
            
            // Clicking bookmark toggles read later status
            binding.ivBookmark.setOnClickListener {
                onBookmarkClick(article)
            }
        }
        
        /**
         * Highlights keywords in the given text by applying a background color span
         */
        private fun highlightKeywords(text: String, keywords: List<String>): SpannableString {
            val spannableString = SpannableString(text)
            
            // Check each keyword against the text
            for (keyword in keywords) {
                var startIndex = text.lowercase().indexOf(keyword.lowercase())
                
                // Keep finding and highlighting all occurrences
                while (startIndex >= 0) {
                    val endIndex = startIndex + keyword.length
                    
                    // Apply the highlight
                    spannableString.setSpan(
                        BackgroundColorSpan(KEYWORD_HIGHLIGHT_COLOR),
                        startIndex,
                        endIndex,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    
                    // Look for the next occurrence
                    startIndex = text.lowercase().indexOf(keyword.lowercase(), startIndex + 1)
                }
            }
            
            return spannableString
        }

        /**
         * Format the time as a relative string like "5 minutes ago", "2 hours ago", etc.
         */
        private fun getRelativeTimeSpan(publishTimeMillis: Long): String {
            return TimeUtil.getRelativeTimeString(publishTimeMillis)
        }

        private fun formatUrl(url: String): String {
            // Remove protocol
            var formatted = url.replace(Regex("^(http|https)://"), "")
            
            // Remove www.
            formatted = formatted.replace(Regex("^www\\."), "")
            
            // Find the domain and cut everything after it
            val domainPattern = Regex("([a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+)")
            val matchResult = domainPattern.find(formatted)
            if (matchResult != null) {
                return matchResult.groupValues[1]
            }
            
            // If no match, just return the original without protocol and www
            return formatted
        }
    }
}
// CodeCleaner_End_999e9ecc-6ad8-4135-a7f8-825d62888048
