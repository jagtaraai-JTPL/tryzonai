import asyncio
from admin_scraper import fetch_product_metadata

async def test():
    urls = [
        "https://www.flipkart.com/apple-iphone-15-black-128-gb/p/itm6ac6485515ae4",
        "https://www.amazon.in/dp/B07WHSKV9W",
    ]
    for url in urls:
        print(f"Scraping {url}...")
        res = await fetch_product_metadata(url)
        print(f"Result: {res.get('name')} | Image: {res.get('image_url')} | Error: {res.get('error')}")

if __name__ == "__main__":
    asyncio.run(test())
