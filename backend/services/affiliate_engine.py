"""
services/affiliate_engine.py — Universal Automatic Affiliate Link Generator
Converts any raw product URL (Amazon, Myntra, Flipkart, AJIO, Meesho) 
into an official direct affiliate link automatically using store tags.
"""

import logging
from urllib.parse import urlparse, parse_qs, urlencode, urlunparse
from config import settings

log = logging.getLogger(__name__)

def generate_affiliate_url(raw_url: str, store: str = None, country_code: str = "IN") -> str:
    """
    Takes any raw product URL and automatically appends official direct affiliate tags
    customized by country code (e.g. 'US', 'DE', 'CA', 'GB', 'IN').
    """
    if not raw_url:
        return ""
    
    url_lower = raw_url.lower()
    store_lower = (store or "").lower()
    country_upper = (country_code or "IN").upper()
    
    try:
        # 1. Amazon (Multi-Country Auto Routing)
        if "amazon." in url_lower or store_lower == "amazon":
            parsed = urlparse(raw_url)
            query = parse_qs(parsed.query)
            
            # Route appropriate Amazon Tag by country
            if country_upper in ["US", "UM"]:
                tag = getattr(settings, "amazon_us_tag", "tryzonai-us-20")
            elif country_upper in ["DE", "AT", "CH"]:
                tag = getattr(settings, "amazon_de_tag", "tryzonai-de-21")
            elif country_upper in ["CA"]:
                tag = getattr(settings, "amazon_ca_tag", "tryzonai-ca-20")
            elif country_upper in ["GB", "UK"]:
                tag = getattr(settings, "amazon_gb_tag", "tryzonai-uk-21")
            else:
                tag = getattr(settings, "amazon_tag", "tryzonai-21")
                
            query["tag"] = [tag]
            new_query = urlencode(query, doseq=True)
            return urlunparse((parsed.scheme, parsed.netloc, parsed.path, parsed.params, new_query, parsed.fragment))

        # 2. Flipkart (IN)
        if "flipkart." in url_lower or store_lower == "flipkart":
            parsed = urlparse(raw_url)
            query = parse_qs(parsed.query)
            query["affid"] = [getattr(settings, "flipkart_affid", "tryzonai")]
            new_query = urlencode(query, doseq=True)
            return urlunparse((parsed.scheme, parsed.netloc, parsed.path, parsed.params, new_query, parsed.fragment))

        # 3. Myntra / Zalando / ASOS (Global Fashion Outlets)
        if "myntra." in url_lower or store_lower == "myntra":
            parsed = urlparse(raw_url)
            query = parse_qs(parsed.query)
            query["subid"] = [getattr(settings, "myntra_subid", "tryzonai")]
            new_query = urlencode(query, doseq=True)
            return urlunparse((parsed.scheme, parsed.netloc, parsed.path, parsed.params, new_query, parsed.fragment))

        # 4. Ajio / International Fashion
        if "ajio." in url_lower or store_lower == "ajio":
            parsed = urlparse(raw_url)
            query = parse_qs(parsed.query)
            query["subid"] = [getattr(settings, "ajio_subid", "tryzonai")]
            new_query = urlencode(query, doseq=True)
            return urlunparse((parsed.scheme, parsed.netloc, parsed.path, parsed.params, new_query, parsed.fragment))

        # 5. Admitad Universal Deeplink Auto-Generator
        admitad_pub_id = getattr(settings, "admitad_publisher_id", "").strip()
        if admitad_pub_id:
            from urllib.parse import quote_plus
            subid = getattr(settings, "admitad_subid", "tryzonai")
            return f"https://ad.admitad.com/g/{admitad_pub_id}/?ulp={quote_plus(raw_url)}&subid={subid}"

    except Exception as e:
        log.error(f"Error generating affiliate URL for {raw_url}: {e}")
        return raw_url

    return raw_url
