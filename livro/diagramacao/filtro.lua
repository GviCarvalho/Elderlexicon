-- Filtro do Pandoc: dá ao Markdown do livro a forma das peças do modelo (modelo.typ).
--
-- # Parte I: ...           -> #parte("I")[...]
-- # Capítulo 1: ...        -> #capitulo("Capítulo 1")[...]
-- # Apêndice B: ...        -> #capitulo("Apêndice B")[...]
-- > "..." — Ummia Imhexis  -> #epigrafe[...]
-- > **Na prática.** ...    -> #caixa("pratica")[...]   (também "advertencia" e "regra")
-- **O que você vai aprender:** + lista -> #aprender[...]
-- o primeiro parágrafo depois da primeira seção de um capítulo ganha a capitular

local function raw(s) return pandoc.RawBlock("typst", s) end

local function typst_inlines(inlines)
  return pandoc.write(pandoc.Pandoc({ pandoc.Plain(inlines) }), "typst"):gsub("\n$", "")
end

local function wrap(open, blocks)
  local out = { raw(open .. "[") }
  for _, b in ipairs(blocks) do table.insert(out, b) end
  table.insert(out, raw("]"))
  return out
end

local function str_of(inlines) return pandoc.utils.stringify(inlines) end

local function quote_kind(bq)
  local first = bq.content[1]
  if first and first.t == "Para" and first.content[1] and first.content[1].t == "Strong" then
    local label = str_of(first.content[1])
    if label:match("^Advert") then return "advertencia" end
    if label:match("^Na prática") then return "pratica" end
    return "regra"
  end
  return nil
end

function Pandoc(doc)
  local out = {}
  local blocks = doc.blocks
  local want_drop = false
  local i = 1
  while i <= #blocks do
    local b = blocks[i]
    if b.t == "Header" and b.level == 1 then
      local text = str_of(b.content)
      local parte = text:match("^Parte ([IVXLC]+): ")
      local rotulo, titulo = text:match("^(Capítulo %d+): (.*)$")
      if not rotulo then rotulo, titulo = text:match("^(Apêndice %a+): (.*)$") end
      if parte then
        table.insert(out, raw('#parte("' .. parte .. '")[' .. text:gsub("^Parte [IVXLC]+: ", "") .. "]"))
      elseif rotulo then
        table.insert(out, raw('#capitulo("' .. rotulo .. '")[' .. titulo .. "]"))
        want_drop = true
      else
        table.insert(out, raw("#capitulo(none)[" .. text .. "]"))
        want_drop = true
      end
    elseif b.t == "BlockQuote" then
      local kind = quote_kind(b)
      if kind then
        for _, x in ipairs(wrap('#caixa("' .. kind .. '")', b.content)) do table.insert(out, x) end
      else
        for _, x in ipairs(wrap("#epigrafe", b.content)) do table.insert(out, x) end
      end
    elseif b.t == "Para" and #b.content == 1 and b.content[1].t == "Strong"
        and str_of(b.content[1]):match("^O que você vai aprender") and blocks[i + 1]
        and blocks[i + 1].t == "BulletList" then
      for _, x in ipairs(wrap("#aprender", { blocks[i + 1] })) do table.insert(out, x) end
      i = i + 1
      if blocks[i + 1] and blocks[i + 1].t == "HorizontalRule" then i = i + 1 end
    elseif b.t == "Header" and b.level == 2 then
      table.insert(out, b)
    elseif b.t == "Para" and want_drop and #str_of(b.content) > 200 then
      -- a capitular: a primeira letra do primeiro parágrafo do capítulo
      table.insert(out, raw("#capitular[" .. typst_inlines(b.content) .. "]"))
      want_drop = false
    else
      table.insert(out, b)
    end
    i = i + 1
  end
  return pandoc.Pandoc(out, doc.meta)
end
