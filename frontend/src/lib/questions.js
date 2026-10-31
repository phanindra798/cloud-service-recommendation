// Follow-up questions asked after the user describes their app, and a light
// parser that skips questions the description already answers.

export const REGIONS = [
  { code: "INDIA", label: "India", words: ["india", "indian", "bangalore", "bengaluru", "mumbai", "delhi", "hyderabad", "chennai"] },
  { code: "US", label: "United States", words: ["usa", "u.s.", "united states", "america", "american"] },
  { code: "UK", label: "United Kingdom", words: ["uk", "united kingdom", "britain", "british", "london", "england"] },
  { code: "EUROPE", label: "Germany / EU", words: ["europe", "european", "eu", "germany", "german", "france", "netherlands", "spain", "italy"] },
  { code: "SINGAPORE", label: "Singapore / SE Asia", words: ["singapore", "southeast asia", "malaysia", "indonesia", "thailand", "vietnam", "philippines"] },
  { code: "JAPAN", label: "Japan", words: ["japan", "japanese", "tokyo"] },
  { code: "AUSTRALIA", label: "Australia", words: ["australia", "australian", "sydney", "melbourne", "new zealand"] },
  { code: "BRAZIL", label: "Brazil / South America", words: ["brazil", "brazilian", "south america", "latin america", "argentina", "chile"] },
  { code: "CANADA", label: "Canada", words: ["canada", "canadian", "toronto", "vancouver"] },
  { code: "UAE", label: "UAE / Middle East", words: ["uae", "dubai", "middle east", "saudi", "qatar"] },
];

export const USER_RANGES = [
  { label: "Under 1K", value: 500 },
  { label: "1K – 10K", value: 5_000 },
  { label: "10K – 100K", value: 50_000 },
  { label: "100K – 1M", value: 500_000 },
  { label: "1M+", value: 2_000_000 },
];

export const APP_TYPES = [
  { code: "WEB_APP", label: "Website / web app", words: ["website", "web app", "portfolio", "landing page"] },
  { code: "ECOMMERCE", label: "E-commerce store", words: ["e-commerce", "ecommerce", "online store", "online shop", "shop", "marketplace", "checkout"] },
  { code: "MOBILE_BACKEND", label: "Mobile app backend", words: ["mobile app", "android", "ios", "flutter", "react native"] },
  { code: "SAAS", label: "SaaS / dashboard", words: ["saas", "dashboard", "b2b", "crm", "admin panel"] },
  { code: "CHAT", label: "Chat / messaging", words: ["chat", "messaging", "messenger", "real-time messages"] },
  { code: "SOCIAL", label: "Social / media sharing", words: ["social", "media sharing", "photo sharing", "instagram", "feed"] },
  { code: "VIDEO_STREAMING", label: "Video streaming", words: ["video", "streaming", "stream", "ott", "netflix", "youtube"] },
  { code: "BLOG", label: "Blog / content site", words: ["blog", "news site", "content site", "cms"] },
  { code: "ANALYTICS", label: "Data / analytics", words: ["analytics", "data pipeline", "etl", "events pipeline", "data warehouse"] },
];

const MULTIPLIERS = { k: 1e3, thousand: 1e3, lakh: 1e5, lakhs: 1e5, m: 1e6, mn: 1e6, million: 1e6, millions: 1e6, crore: 1e7 };

function hasWord(text, word) {
  const escaped = word.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(^|[^a-z])${escaped}([^a-z]|$)`).test(text);
}

/** Returns what the description already tells us: { region, users, appType } (codes / numbers or null). */
export function detectAnswers(description) {
  const text = description.toLowerCase();

  const region = REGIONS.find((r) => r.words.some((w) => hasWord(text, w)));
  const appType = APP_TYPES.find((a) => a.words.some((w) => hasWord(text, w)));

  let users = null;
  const m = text.match(/(\d[\d,]*(?:\.\d+)?)\s*(k|thousand|lakhs?|mn|m|millions?|crore)?\+?\s*(?:monthly\s+|daily\s+|active\s+)*(users|customers|members|people|subscribers|visitors|students|players)/);
  if (m) {
    const n = parseFloat(m[1].replace(/,/g, ""));
    users = Math.round(n * (MULTIPLIERS[m[2]] || 1));
  }

  return { region: region ? region.code : null, users, appType: appType ? appType.code : null };
}

/** Which questions still need asking, given what the description already says. */
export function pendingQuestions(description) {
  const known = detectAnswers(description);
  const questions = [];
  if (!known.region) questions.push("region");
  if (!known.users) questions.push("users");
  if (!known.appType) questions.push("appType");
  questions.push("highAvailability"); // rarely stated, and changes the design
  return { questions, known };
}
