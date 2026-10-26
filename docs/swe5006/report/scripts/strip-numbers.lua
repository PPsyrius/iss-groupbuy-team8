-- The template's heading styles number themselves (Heading 1-3 carry numPr),
-- so drop the "3.4.1 " prefix the Markdown keeps for review and the check script.
function Header(h)
  local first, second = h.content[1], h.content[2]
  if first and first.t == 'Str' and first.text:match('^%d+[%.%d]*$') and second and second.t == 'Space' then
    h.content:remove(1)
    h.content:remove(1)
  end
  return h
end
