// A network-level failure (offline, DNS, CORS, the Gateway unreachable entirely) throws a
// raw browser error like "Failed to fetch" before a response ever comes back -- never
// something to show a user verbatim. This turns that one case into plain language while
// leaving every other error message untouched (most are already written to be user-facing,
// e.g. bffLogin's "Incorrect email or password"). Display-only: never changes what actually
// triggers success or failure, only what text gets shown once it already has.
export function friendlyErrorMessage(err) {
  if (err instanceof TypeError) {
    return "Can't reach the server right now. Check your connection and try again.";
  }
  return err.message;
}
