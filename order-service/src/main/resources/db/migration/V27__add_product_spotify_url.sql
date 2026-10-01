-- An optional Spotify track/album/playlist share link per product -- see Product#spotifyUrl.
-- Same length as video_url: both are "a URL, nothing more" columns.
ALTER TABLE product ADD COLUMN spotify_url VARCHAR(500);
