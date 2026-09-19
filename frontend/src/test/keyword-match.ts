// Mirrors the backend's UC1-ext search (`BizProductMapper.selectVisibleSearchPage`, PR #107):
// whitespace-separated terms AND-combine, and each term matches `title`, `subtitle` or
// `description` case-insensitively (ILIKE). Both mocks (the browser seed and the route test) use
// this one matcher so a multi-term or description-only search behaves like the API
// (CodeRabbit r4045128176 on PR #119).
export type KeywordFields = {
  title?: string | null;
  subtitle?: string | null;
  description?: string | null;
};

export function keywordTerms(keyword: string | null | undefined): string[] {
  return (keyword ?? "").toLowerCase().split(/\s+/).filter(Boolean);
}

export function matchesKeyword(record: KeywordFields, terms: string[]): boolean {
  if (terms.length === 0) return true;
  const fields = [record.title, record.subtitle, record.description].map((f) =>
    (f ?? "").toLowerCase(),
  );
  return terms.every((t) => fields.some((f) => f.includes(t)));
}
