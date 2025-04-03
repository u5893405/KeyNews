# AI Filtering in KeyNews

This document explains how to use the new AI filtering feature in KeyNews.

## Overview

AI filtering uses Gemini API to filter news articles based on natural language rules. This allows for more sophisticated filtering than keyword-based filtering.

## Setting Up

1. **Set your Gemini API key**:
   - Go to Settings
   - Enter your Gemini API key 
   - Save the key

2. **Create AI Filtering Rules**:
   - Open the side menu
   - Tap "AI Filtering"
   - Tap the "+" button to create a new rule
   - Enter a name for the rule
   - Choose whether it's a whitelist or blacklist rule
   - Enter the rule text (e.g., "articles about important economic news" or "articles about celebrity gossip")
   - Save the rule

3. **Apply AI Filtering to a Reading Feed**:
   - Go to the Reading Feeds screen
   - Edit an existing feed or create a new one
   - Under "AI Filtering Rules", select one whitelist rule and/or one blacklist rule
   - Save the feed

## How It Works

1. Articles are first filtered by keywords (if any keyword rules are selected for the feed)
2. Then, the remaining articles are filtered by AI rules:
   - If a whitelist rule is selected, only articles matching that rule will be kept
   - If a blacklist rule is selected, any articles matching that rule will be removed
   - If both rules are selected, whitelist has priority over blacklist

## Tips for Writing Effective AI Rules

- Be specific about what you want to include or exclude
- Focus on topics, not specific words (the AI understands concepts)
- Examples:
  - "Articles about major political events that impact international relations"
  - "News related to economic policy changes or market trends"
  - "Updates on technological innovations in healthcare or renewable energy"
  - "Articles about celebrity gossip, sports scores, or weather forecasts"

## Using the Gemini API

KeyNews uses the "gemini-2.0-flash-lite" model, which is optimized for quick, efficient text classification.

## Performance Considerations

- AI filtering occurs only when displaying articles or starting a reading session
- The app caches results to avoid unnecessary API calls
- For performance reasons, AI filtering is only applied when necessary (e.g., when displaying articles or starting a reading session)
