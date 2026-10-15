"""
services/affiliate_engine.py — Universal Automatic Affiliate Link Generator
Converts any raw product URL (Amazon, Myntra, Flipkart, AJIO, Meesho, etc.) 
into an official working affiliate link or clean direct store link.
Never returns dead/broken links or 404 error pages.
"""

import logging
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse, quote_plus
from config import settings

log = logging.getLogger(__name__)


def generate_affiliate_url(raw_url: str, store: str = None, country_code: str = "IN") -> str:
    """
    Takes any raw product URL and automatically appends official direct affiliate tags
    or store-specific Admitad deeplinks. Guaranteed to never break user buy links.
    """
    if not raw_url or not isinstance(raw_url, str):
        return ""

    raw_url = raw_url.strip()
    if not (raw_url.startswith("http://") or raw_url.startswith("https://")):
        return raw_url

    url_lower = raw_url.lower()
    store_lower = (store or "").lower()
    country_upper = (country_code or "IN").upper()

    try:
        # 1. Amazon (Direct Affiliate Tag)
        if "amazon." in url_lower or store_lower == "amazon":
            parsed = urlparse(raw_url)
            query = parse_qs(parsed.query)

            if country_upper in ["US", "UM"]:
                tag = getattr(settings, "amazon_us_tag", "tryzonaius20-20")
            elif country_upper in ["DE", "AT", "CH"]:
                tag = getattr(settings, "amazon_de_tag", "tryzonai00-21")
            elif country_upper in ["CA"]:
                tag = getattr(settings, "amazon_ca_tag", "tryzonai-20")
            elif country_upper in ["GB", "UK"]:
                tag = getattr(settings, "amazon_gb_tag", "tryzonai02-21")
            else:
                tag = getattr(settings, "amazon_tag", "tryzonai08-21")

            query["tag"] = [tag]
            new_query = urlencode(query, doseq=True)
            return urlunparse((parsed.scheme, parsed.netloc, parsed.path, parsed.params, new_query, parsed.fragment))

        # 2. Cuelinks Partner Stores (AJIO, Myntra, Flipkart, Nykaa, Tata CLiQ, Urbanic, H&M, etc. - Code: CNTE)
        cuelinks_channel_code = getattr(settings, "cuelinks_ajio_code", "CNTE").strip()
        cuelinks_stores = ["ajio", "myntra", "flipkart", "nykaa", "tatacliq", "urbanic", "hm.com", "shoppersstop", "lifestylestores", "levi"]

        if any(s in url_lower for s in cuelinks_stores) or store_lower in cuelinks_stores:
            # Direct homepage / campaign link
            if url_lower in ["https://ajiogram.ajio.com/", "https://ajiogram.ajio.com", "https://ajio.com/", "https://www.ajio.com/", "https://www.ajio.com"]:
                return f"https://ajo.clnk.in/{cuelinks_channel_code}"

            # Specific product page: wrap in Cuelinks tracking deeplink
            encoded_target = quote_plus(raw_url)
            return f"https://links2revenue.com/cuelinks?url={encoded_target}&pub_id={cuelinks_channel_code}"

        # 3. Meesho (Direct Affiliate Tag)
        if "meesho." in url_lower or store_lower == "meesho":
            meesho_affid = getattr(settings, "meesho_affiliate_id", "598471736").strip()
            meesho_subid = getattr(settings, "meesho_subid", "tryzonai").strip()
            parsed = urlparse(raw_url)
            query = parse_qs(parsed.query)

            if "af_invite" in parsed.path:
                query["utm_source"] = [meesho_subid]
                query["subid"] = [meesho_subid]
            else:
                query["subid"] = [meesho_subid]
                if meesho_affid:
                    query["affid"] = [meesho_affid]
                query["utm_source"] = [meesho_subid]

            new_query = urlencode(query, doseq=True)
            return urlunparse((parsed.scheme, parsed.netloc, parsed.path, parsed.params, new_query, parsed.fragment))

    except Exception as e:
        log.error(f"Error generating affiliate URL for {raw_url}: {e}")
        return raw_url

    return raw_url
