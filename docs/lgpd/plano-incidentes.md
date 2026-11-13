# Plano de Resposta a Incidentes de Segurança — Farol

Base: LGPD art. 48 e Resolução CD/ANPD nº 15/2024 (comunicação em até **3 dias úteis** a partir do conhecimento; prazo em dobro para agente de pequeno porte; complemento em até 20 dias úteis).

## 1. O que é incidente
Qualquer evento confirmado que afete a confidencialidade, a integridade ou a disponibilidade de dados pessoais. Exemplos: vazamento do banco D1, token do GitHub ou da Cloudflare exposto, APK adulterado publicado, acesso indevido a uma conta.

## 2. Passo a passo
| Quando | Ação |
|---|---|
| Na hora | **Conter:** revogar tokens (GitHub, Cloudflare), trocar o `ADMIN_KEY`, desativar contas afetadas pelo painel, e se for preciso ligar `REQUIRE_LOGIN=1` ou pausar o worker |
| Dia 0 | **Registrar:** data e hora do conhecimento, o que aconteceu, dados e contas afetados (manter o registro por 5 anos) |
| Dia 0 a 1 | **Avaliar o risco:** dados afetados, quantos titulares, se houve exposição pública, que dano pode causar (fraude, constrangimento) |
| Até 3 dias úteis* | **Se houver risco ou dano relevante:** comunicar a ANPD (formulário em gov.br/anpd) e os titulares (e-mail ou aviso no app), dizendo o que houve, quais dados, os riscos, as medidas e o contato |
| Até 20 dias úteis | Complementar a comunicação à ANPD, se a primeira foi preliminar |
| Depois | **Corrigir a causa,** revisar este plano e o RIPD, e registrar as lições aprendidas |

\* 6 dias úteis se o controlador se enquadrar como agente de tratamento de pequeno porte.

## 3. Contatos
- Canal do titular: `CONTATO_PRIVACIDADE`
- ANPD: gov.br/anpd → Comunicação de Incidente de Segurança (CIS)
