// Turns the share link Product#spotifyUrl actually stores (what "Share > Copy link"
// gives you, e.g. https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT?si=...) into the
// iframe src Spotify's own embed player expects (.../embed/track/<id>, no query string).
// Validated server-side already (see SetProductSpotifyRequest's @Pattern) -- this never
// has to handle a link that doesn't match the expected shape, but falls back to null
// rather than rendering a broken iframe if one somehow did.
const SHARE_URL_PATTERN = /^https:\/\/open\.spotify\.com\/(track|album|playlist)\/([A-Za-z0-9]+)(\?.*)?$/;

export function toSpotifyEmbedUrl(spotifyUrl) {
  const match = SHARE_URL_PATTERN.exec(spotifyUrl ?? "");
  if (!match) return null;
  const [, type, id] = match;
  return `https://open.spotify.com/embed/${type}/${id}`;
}
