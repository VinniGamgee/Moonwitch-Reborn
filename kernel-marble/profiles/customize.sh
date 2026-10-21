case "$(getprop ro.product.device)" in
  marble|marblein) ;;
  *) abort "Este modulo e exclusivo para marble / POCO F5." ;;
esac
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/profilectl" 0 0 0755
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
ui_print 'O botao Acao alterna os perfis. Padrao inicial: balanced.'
