async function searchYoutube(query) {
    const res = await fetch(`https://itunes.apple.com/search?term=${encodeURIComponent(query)}&limit=1&entity=song`);
    const data = await res.json();
    console.log(data.results[0].previewUrl);
}
searchYoutube('lofi hip hop');
