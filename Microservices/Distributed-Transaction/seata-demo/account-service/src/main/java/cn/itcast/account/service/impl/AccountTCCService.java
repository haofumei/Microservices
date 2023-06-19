package cn.itcast.account.service.impl;

import cn.itcast.account.entity.AccountFreeze;
import cn.itcast.account.mapper.AccountFreezeMapper;
import cn.itcast.account.mapper.AccountMapper;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AccountTCCService implements cn.itcast.account.service.AccountTCCService {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountFreezeMapper accountFreezeMapper;

    @Override
    public void deduct(String userId, int money) {
        String xid = RootContext.getXID();
        // 0. reject business suspension
        AccountFreeze oldFreeze = accountFreezeMapper.selectById(xid);
        if (oldFreeze != null) {
            return;
        }
        // 1. deduct money
        accountMapper.deduct(userId, money);
        // 2. record freeze money and state
        AccountFreeze freeze = new AccountFreeze();
        freeze.setUserId(userId);
        freeze.setFreezeMoney(money);
        freeze.setState(AccountFreeze.State.TRY);
        freeze.setXid(xid);

        accountFreezeMapper.insert(freeze);
    }

    @Override
    public boolean confirm(BusinessActionContext ctx) {
        String xid = ctx.getXid();
        // 1. delete freeze record
        int count = accountFreezeMapper.deleteById(xid);
        return count == 1;
    }

    @Override
    public boolean cancel(BusinessActionContext ctx) {
        // 1.1 check freeze record
        String xid = ctx.getXid();
        String userId = ctx.getActionContext("userId").toString();
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(xid);
        // 1.2 empty rollback
        if (accountFreeze == null) {
            AccountFreeze freeze = new AccountFreeze();
            freeze.setUserId(userId);
            freeze.setFreezeMoney(0);
            freeze.setState(AccountFreeze.State.CANCEL);
            freeze.setXid(xid);
        }
        // 1.3 idempotent
        if (accountFreeze.getState() == AccountFreeze.State.CANCEL) {
            return true;
        }
        // 2. refund
        accountMapper.refund(accountFreeze.getUserId(), accountFreeze.getFreezeMoney());
        // 3. reset freeze money to 0, state to CANCEL
        accountFreeze.setFreezeMoney(0);
        accountFreeze.setState(AccountFreeze.State.CANCEL);
        int count = accountFreezeMapper.updateById(accountFreeze);
        return count == 1;
    }
}
