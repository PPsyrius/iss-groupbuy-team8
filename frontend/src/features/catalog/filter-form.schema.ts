import * as v from "valibot";

// The keyword form behind the catalog's search field (plan §4 T2-03, artboard deviation in
// §12 note 44: the category is a chip row of links, so only the keyword goes through TanStack
// Form). The contract caps `keyword` at 64 characters; whitespace-separated terms all have to
// match, so leading and trailing whitespace carries no meaning and is trimmed.
export const KEYWORD_MAX_LENGTH = 64;

export const filterFormSchema = v.object({
  keyword: v.pipe(
    v.string(),
    v.trim(),
    v.maxLength(KEYWORD_MAX_LENGTH, `Keep the search under ${KEYWORD_MAX_LENGTH} characters.`),
  ),
});
