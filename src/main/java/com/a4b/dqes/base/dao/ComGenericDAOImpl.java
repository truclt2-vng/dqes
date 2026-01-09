package com.a4b.dqes.base.dao;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;

import com.a4b.core.server.enums.AuthStatus;
import com.googlecode.genericdao.dao.jpa.GenericDAOImpl;
import com.googlecode.genericdao.search.jpa.JPASearchProcessor;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.BooleanPath;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.DateTimePath;
import com.querydsl.core.types.dsl.EntityPathBase;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public abstract class ComGenericDAOImpl<T, Q extends EntityPathBase<T>, ID extends Serializable>
        extends GenericDAOImpl<T, ID> implements ComGenericDAO<T, ID> {

    // ==== Field name constants (chuẩn hoá tên chung) ====
    protected static final String F_APP_CODE       = "appCode";
    protected static final String F_TENANT_CODE    = "tenantCode";
    protected static final String F_AUTH_STATUS    = "authStatus";
    protected static final String F_RECORD_STATUS  = "recordStatus";
    protected static final String F_CURRENT_FLG    = "currentFlg";
    protected static final String F_EFFECTIVE_FROM = "effectiveStart";
    protected static final String F_EFFECTIVE_TO   = "effectiveEnd";
    protected static final String F_ID             = "id";
    protected static final String F_CODE           = "code";
    protected static final String F_AGG_ID         = "aggId";

    protected static final String DEFAULT_RECORD_STATUS = "O";
    protected static final String DEFAULT_AUTH_STATUS   = "A";

    protected JPAQueryFactory queryFactory;

    // PathBuilder tạo từ q(), dùng cho mọi path động theo tên
    private PathBuilder<T> pb;

    /** Subclass vẫn cung cấp Q instance như trước */
    protected abstract Q q();

    protected StringPath codePath(){
        return pb.getString(F_CODE);
    }

    @Override
    @PersistenceContext
    public void setEntityManager(EntityManager entityManager) {
        super.setEntityManager(entityManager);
        this.queryFactory = new JPAQueryFactory(em());
        // alias từ metadata của Q, type từ Q
        this.pb = new PathBuilder<>(q().getType(), q().getMetadata().getName());
    }

    @Override
    public GenericPredicateBuilder<T> predicateBuilder() {
        return new GenericPredicateBuilder<>(persistentClass);
    }

    @Override
    @Autowired
    public void setSearchProcessor(JPASearchProcessor searchProcessor) {
        super.setSearchProcessor(searchProcessor);
    }

    // ====== Helper lấy Path theo tên (KHÔNG accessor, KHÔNG reflection) ======
    protected StringPath sp(String field) { return pb.getString(field); }
    protected BooleanPath bp(String field) { return pb.getBoolean(field); }
    protected DateTimePath<Date> dpp(String field) { return pb.getDateTime(field, Date.class); }
    protected <N extends Number & Comparable<N>> NumberPath<N> np(String field, Class<N> type) {
        return pb.getNumber(field, type);
    }
    protected <V extends Comparable<V>> ComparableExpression<V> cp(String field, Class<V> type) {
        return pb.getComparable(field, type);
    }
    protected <V> SimpleExpression<V> xp(String field, Class<V> type) {
        return pb.get(field, type);
    }

    // ====================== Common filter ======================
    protected BooleanBuilder baseAppTenant(String appCode, String tenantCode) {
        BooleanBuilder b = new BooleanBuilder();
        if (appCode != null)    b.and(sp(F_APP_CODE).eq(appCode));
        if (tenantCode != null) b.and(sp(F_TENANT_CODE).eq(tenantCode));
        return b;
    }

    protected BooleanBuilder baseAuthorized() {
        return new BooleanBuilder().and(sp(F_AUTH_STATUS).eq(DEFAULT_AUTH_STATUS));
    }

    protected BooleanBuilder baseUnauthorized() {
        return new BooleanBuilder().and(sp(F_AUTH_STATUS).eq(AuthStatus.U.name()));
    }

    protected BooleanBuilder baseEffective() {
        return new BooleanBuilder().and(bp(F_CURRENT_FLG).isTrue());
    }

    protected BooleanExpression effectiveAt(Date atTime) {
        return dpp(F_EFFECTIVE_FROM).loe(atTime)
               .and(dpp(F_EFFECTIVE_TO).goe(atTime).or(dpp(F_EFFECTIVE_TO).isNull()));
    }

    // ====================== Find methods ======================

    @Override
    public T findEffectiveByCode(String code, String appCode, String tenantCode) {
        BooleanBuilder where = new BooleanBuilder()
            .and(codePath().eq(code))
            .and(baseAppTenant(appCode, tenantCode))
            .and(baseEffective());
        return queryFactory.selectFrom(q()).where(where).fetchFirst();
    }

    @Override
    public T findEffectiveByAggId(UUID aggId, String appCode, String tenantCode) {
        BooleanBuilder where = new BooleanBuilder()
            .and(cp(F_AGG_ID, UUID.class).eq(aggId))
            .and(baseAppTenant(appCode, tenantCode))
            .and(baseEffective());
        return queryFactory.selectFrom(q()).where(where).fetchFirst();
    }

    @Override
    public T findByAggIdAndAuthStatus(UUID aggId, String authStatus, String appCode, String tenantCode) {
            BooleanBuilder where = new BooleanBuilder()
                    .and(cp(F_AGG_ID, UUID.class).eq(aggId))
                    .and(baseAppTenant(appCode, tenantCode))
                    .and(sp(F_AUTH_STATUS).eq(authStatus))
            ;
            if(AuthStatus.A.name().equals(authStatus)) {
                    where.and(baseEffective());
            }
            return queryFactory.selectFrom(q()).where(where).fetchFirst();
    }

    @Override
    public T findByAggId(UUID aggId, String appCode, String tenantCode) {
        BooleanBuilder base = new BooleanBuilder()
            .and(cp(F_AGG_ID, UUID.class).eq(aggId))
            .and(baseAppTenant(appCode, tenantCode))
        ;

        BooleanExpression preferU = sp(F_AUTH_STATUS).eq(AuthStatus.U.name());
        BooleanExpression fallbackA = sp(F_AUTH_STATUS).eq(AuthStatus.A.name()).and(bp(F_CURRENT_FLG).isTrue());

        // Sắp xếp: ưu tiên 'U' trước
        OrderSpecifier<Integer> preferUFirst = new CaseBuilder().when(sp(F_AUTH_STATUS).eq(AuthStatus.U.name())).then(0).otherwise(1).asc();

        return queryFactory.selectFrom(q()).where(base.and(preferU.or(fallbackA))).orderBy(preferUFirst).fetchFirst();
    }

    @Override
    public T findById(Long id, String appCode, String tenantCode) {
        BooleanBuilder where = new BooleanBuilder()
            .and(np(F_ID, Long.class).eq(id))
            .and(baseAppTenant(appCode, tenantCode))
            ;
        return queryFactory.selectFrom(q()).where(where).fetchFirst();
    }

    @Override
    public List<T> findUnAuthorizedByIds(List<Long> ids, String appCode, String tenantCode) {
        BooleanBuilder where = new BooleanBuilder()
            .and(np(F_ID, Long.class).in(ids))
            .and(baseAppTenant(appCode, tenantCode))
            .and(baseUnauthorized());
        return queryFactory.selectFrom(q()).where(where).fetch();
    }

    @Override
    public List<T> findByIds(List<Long> ids, String appCode, String tenantCode) {
        BooleanBuilder where = new BooleanBuilder()
            .and(np(F_ID, Long.class).in(ids))
            .and(baseAppTenant(appCode, tenantCode));
        return queryFactory.selectFrom(q()).where(where).fetch();
    }

    @Override
    public boolean existsByCode(String code, String appCode, String tenantCode){
        if(code == null || code.isEmpty()) {
            return false;
        }
        return queryFactory.selectOne()
                .from(q())
                .where(
                    codePath().eq(code),
                    sp(F_APP_CODE).eq(appCode),
                    sp(F_TENANT_CODE).eq(tenantCode)
                )
                .fetchFirst() != null;
    }
    @Override
    public boolean existsUnauthorized(String code, Long excludeId, String appCode, String tenantCode){
        if(code == null || code.isEmpty()) {
            return false;
        }
        return queryFactory.selectOne()
                .from(q())
                .where(
                    codePath().eq(code),
                    np(F_ID, Long.class).ne(excludeId),
                    sp(F_APP_CODE).eq(appCode),
                    sp(F_TENANT_CODE).eq(tenantCode),
                    sp(F_AUTH_STATUS).eq(AuthStatus.U.name())
                )
                .fetchFirst() != null;
    }

    @Override
    public List<T> findAll(Predicate predicate, String appCode, String tenantCode) {
        BooleanBuilder where = new BooleanBuilder();
        if (predicate != null) where.and(predicate);
        where.and(baseAppTenant(appCode, tenantCode));
        return queryFactory.selectFrom(q()).where(where).fetch();
    }

    @Override
    public T findOne(Predicate predicate, String appCode, String tenantCode) {
        BooleanBuilder where = new BooleanBuilder();
        if (predicate != null) where.and(predicate);
        where.and(baseAppTenant(appCode, tenantCode));
        return queryFactory.selectFrom(q()).where(where).fetchFirst();
    }

    @Override
    public List<T> findList(BooleanBuilder expr) {
        return queryFactory.selectFrom(q()).where(expr).fetch();
    }

    @Override
    public Stream<T> streamAll(String appCode, String tenantCode) {
        BooleanBuilder where = new BooleanBuilder();
        where.and(baseAppTenant(appCode, tenantCode));
        return queryFactory.selectFrom(q()).where(where).stream();
    }
}
