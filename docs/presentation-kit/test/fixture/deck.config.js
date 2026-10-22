// Minimal deck for the kit's tests: three main slides (one with a fragment, one hosting a demo) and two backups.
module.exports = {
  title: 'Fixture deck',
  brand: 'Test',
  darkSlides: [1],
  fillLabel: 'FILL',
  guidelineLabel: 'Guideline',
  demoSegment: 2,
  motion: false,
  outputs: { deck: 'fixture.html', deckPdf: 'fixture.pdf', scriptPdf: 'fixture-script.pdf' },
  scriptTitle: 'Fixture script',
  scriptHeader: { left: 'Fixture', right: 'Test' },
};
