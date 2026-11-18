package dev.gamecore.platform;
/** 可公开的稳定错误码，不携带SQL、密码、凭证或堆栈内容。 */
public final class ApiProblem extends RuntimeException {
    private static final long serialVersionUID=1L;
    final int status;
    public ApiProblem(int status,String code){super(code);this.status=status;}
    static void require(boolean condition,int status,String code){if(!condition){throw new ApiProblem(status,code);}}
}
