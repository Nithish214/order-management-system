package com.learn.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetProductSpotifyRequest {

    // Spotify's own "Share > Copy link" format: https://open.spotify.com/track/<id>,
    // .../album/<id>, or .../playlist/<id>, optionally with a ?si=... tracking parameter
    // Spotify appends itself (allowed here, stripped later -- see utils/spotify.js on the
    // frontend). Whitelisted to exactly this shape, the same reasoning ProductController's
    // own ?sort= whitelist uses: this value gets embedded directly as an iframe src, so
    // accepting an arbitrary URL here would let an admin account (compromised or just
    // careless) turn this product page into an iframe for any site at all, not merely a
    // broken Spotify link.
    @NotBlank(message = "spotifyUrl is required")
    @Pattern(
            regexp = "^https://open\\.spotify\\.com/(track|album|playlist)/[A-Za-z0-9]+(\\?.*)?$",
            message = "spotifyUrl must be a Spotify track, album, or playlist link (https://open.spotify.com/...)"
    )
    private String spotifyUrl;
}
