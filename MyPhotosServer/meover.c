
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>
#include <signal.h>
#include <libpq-fe.h>

void sigint_handler(int signum);  // обработка сигнала Ctrl+C

int PORT = 45138;
char *welcome = "Welcome to the server\n";
#define BUFLEN 4096
#define MAX_CLIENTS 6
const static char conninfo[] = "hostaddr=82.179.140.18 port=5432 dbname=myPhotos user=mpi password=135a1";
const char* HELP_MESSAGE = "/quit - выйти с сервера;\n/register - зарегистрировать новый аккаунт;\n/login - войти в существующий аккаунт;\n";
const char* QUIT_MESSAGE = "Goodbye.";
const char* key = "137Meow137";
const char* blockerator = "Access denied.";
int sockfd;  // дескриптор сокета 
PGconn* conn; // объект подключения

// Обработка сигнала Ctrl+C
void sigint_handler(int signum)
{
    close(sockfd);
    PQfinish(conn);
    exit(0);
}

enum RequestType
{
    GET,
    POST,
};

enum ResponseType
{
    SUCCESS,
    FAIL,
};

struct Request
{
    enum RequestType type;
    char* command;
    char* request_string; 
};

struct Response
{
    enum ResponseType type;
    char* response_message;
};

int checkMultipleObjects(char* string){
    int numLines = 0;
    for (const char *c = string; *c != '\0'; ++c){
        if (*c == '\n'){
            numLines++;
            if(numLines > 1){
                return 1;
            }
        }
    }
    return 0;
}

//column_name:column_value;column_name:column_value
void serialize(char* string, char* serialized)
{
    printf("\n-----------------------\nBEFORE SERIALIZATION\n\"%s\"",string);
    if(strcmp(string," ")==0){
        return;
    }
    char* row_string = malloc(sizeof(char)*256);
    char *row,*token, *rest;
    char *prop_name, *prop_value;
    char *tmp, *rowtmp;


    rest = strdup(string);

    int multipleRows = checkMultipleObjects(rest);

    if(multipleRows == 1){
        strcat(serialized,"[{");
    }
    else{
        strcat(serialized,"{");
    }
    while((row=strsep(&rest,"\n"))!=NULL){
        rowtmp = strdup(row);
        while( (token=strsep(&rowtmp,";")) != NULL)
        {
            tmp = strdup(token);
            prop_name = strtok(tmp,":");
            prop_value = strtok(NULL,":");
            if(prop_name == NULL || prop_value == NULL){
                break;
            }
            sprintf(row_string, "\"%s\":\"%s\",",prop_name,prop_value);
            strcat(serialized,row_string);
        }
        serialized[strlen(serialized)-1]=' ';
        if(multipleRows == 1 && strcmp(row,"") != 0){
            strcat(serialized,"}, {");
        }
    }
    if(multipleRows == 1){
        serialized[strlen(serialized) - 3] = ']';
        serialized[strlen(serialized) - 2] = ' ';
        serialized[strlen(serialized) - 1] = ' ';
    }
    else{
        strcat(serialized,"}");
    }

    printf("\n-----------------------\nAFTER SERIALIZATION\n%s",serialized);
    return;
}

void deserialize(char* string,char* deserialized)
{    
    printf("\n-----------------------\nBEFORE DESERIALIZATION\n%s",string);
    char *token, *row;
    char *prop_value;
    char *rest;
    char *tmp, *tmptok;

    char* serialized = strdup(string);
    row = strtok(serialized,":");
    row = strtok(NULL,":");
    rest = strdup(row);
    while( (token=strsep(&rest,",")) != NULL)
    {
        tmp = strdup(token);
        tmptok = strsep(&tmp,"\"");
        if(tmptok == NULL){
            prop_value = token;
        }
        else{
            while((tmptok = strsep(&tmp,"\"")) != NULL){
                if(strcmp(tmptok,"") != 0 && strcmp(tmptok," ") != 0 && strcmp(tmptok,"}") != 0){
                    prop_value = tmptok;
                }
            }   
        }
        strcat(deserialized,prop_value);
        strcat(deserialized,",");
        rest = strtok(NULL,":");
        if(rest == NULL){
            break;
        }
    }

    printf("\n-----------------------\nAFTER DESERIALIZATION\n%s",deserialized);
    return;
}

char* serialize_response(char* response_type, char* response_message){
    printf("response_message: %s\n",response_message);
    char serialized[BUFLEN]="";
    strcat(serialized, "{");
    strcat(serialized, "\"type\":\"");
    strcat(serialized, response_type);
    strcat(serialized,"\",\"message\":\"");
    strcat(serialized, response_message);
    strcat(serialized,"\"}\n");
    printf("serialized: %s\n",serialized);
    char* res = serialized;
    return res;
}

void executeSQL(PGconn* conn, char* request, const char **param_values, int num_params, char* result_string)
{
    printf("\n-----------------------\n SQL to execute\n Request:%s\n",request);
    char* result = (char*)malloc(BUFLEN);
    memset(result, 0, BUFLEN);

    // Начать транзакцию
    PGresult* res = PQexec(conn, "BEGIN TRANSACTION");
    if (PQresultStatus(res) != PGRES_COMMAND_OK)
    {
        fprintf(stderr, "BEGIN command failed: %s", PQerrorMessage(conn));
        exit(1);
    }

    // Очистить res, чтобы избежать утечки памяти
    PQclear(res);
    // Выполнить запрос
    res = PQexecParams(conn, request, num_params, NULL, param_values, NULL, NULL, 0);

    if (PQresultStatus(res) == PGRES_COMMAND_OK)
    {
        int affected_rows = atoi(PQcmdTuples(res));
        snprintf(result, BUFLEN, "%d", affected_rows);
    }
    else if (PQresultStatus(res) == PGRES_TUPLES_OK)
    {
        for (int i = 0; i < PQntuples(res); i++)
        {
            for (int j = 0; j < PQnfields(res); j++)
            {
                strcat(result,PQfname(res,j));
                strcat(result,":");
                strcat(result, PQgetvalue(res, i, j));
                strcat(result, ";");
            }
            strcat(result, "\n");
        }
        if (strlen(result) > 0){
            strcat(result, "\n");
            result[strlen(result) - 1] = '\0';
        }
        if (PQntuples(res) == 0)
            result[0] = ' ';
    }
    else
    {
        fprintf(stderr, "Sql command failed: %s", PQerrorMessage(conn));
        snprintf(result, BUFLEN, "%d", -1);
    }
    printf("%s\n",result);
    // Очистить res
    PQclear(res);

    // Завершить транзакцию
    res = PQexec(conn, "COMMIT");
    PQclear(res);

    strcpy(result_string,result);
}

int processResponse(int fd, enum ResponseType response_type, char* response_message){
    printf("bibibi%s\n",response_message);
    char* res_message = strdup(response_message);
    char* serialize_result;
    int sendRes;
    switch(response_type)
    {
        case SUCCESS:
            printf("SUCCESS\n");
            serialize_result = serialize_response("SUCCESS",res_message);
            break;
        case FAIL:
            printf("FAIL\n");
            serialize_result = serialize_response("FAIL",res_message);
            break;
        default:
            printf("Bad request\n");
            serialize_result = serialize_response("FAIL","Bad request");
            break;
    }
    printf("%s\n", serialize_result);
    sendRes = send(fd, serialize_result, BUFLEN, 0);
    if(sendRes<0){
        printf("Send response failed.\n");
        return 0;
    }
    printf("Response sent.\n");
    return 0;
}

struct Response* processRequest(PGconn* conn,int fd,char* command, char* request_string){
    printf("bibibi\n");
    printf("command: %p\nrequest: %p\n",command,request_string);

    enum ResponseType type;
    type = SUCCESS;
    struct Response response;
    char message[BUFLEN] = "";
    response.type = type;
    response.response_message = message;
    struct Response* response_p = &response;
 
    if(strcmp(command,"/help")==0){
        printf("\nhelp\n");
        type = SUCCESS;
        response_p->type = type;
        strcpy(response_p->response_message, HELP_MESSAGE);
        return response_p;
    }

    if(strcmp(command,"/quit")==0){
        printf("\nquit\n");
        type = SUCCESS;
        response_p->type = type;
        strcpy(response_p->response_message, QUIT_MESSAGE);
        return response_p;
    }

    char sql_result [BUFLEN];
    char* dup = strdup(request_string);

    if(strcmp(command,"/login")==0){
        printf("\nlogin\n");
        char* par1 = strtok(dup,"@");
        char* par2 = strtok(NULL,"@");
        const char* parameter[] = {par1,par2};
        parameter[0] = par1;
        parameter[1] = par2;
        printf("%s\n%s\n",par1,par2);
        executeSQL(conn, "SELECT * FROM users WHERE login =$1 AND password = $2", parameter, 2,sql_result);

        if(strlen(sql_result) == 1){
            type = FAIL;
            response_p->type = type;
            strcpy(response_p->response_message, "Incorrect login or password.");
            return response_p;
        }

        type = SUCCESS;
        response_p->type = type;
        char serialized[BUFLEN] = "";
        serialize(sql_result,serialized);
        strcpy(response_p->response_message,serialized);
        return response_p;
    }

    if(strcmp(command,"/register")==0){
        printf("\nregister\n");
        char* par1 = strtok(dup,"@");
        char* par2 = strtok(NULL,"@");
        const char* parameter[] = {par1,par2};
        parameter[0] = par1;
        parameter[1] = par2;
        printf("%s\n%s\n",par1,par2);
        executeSQL(conn, "SELECT * FROM users WHERE login =$1 AND password = $2", parameter, 2,sql_result);

        if(strlen(sql_result) != 1){
            type = FAIL;
            response_p->type = type;
            response_p->response_message = "User already exists.";
            printf("%s\n",response_p->response_message);
            return response_p;
        }

        executeSQL(conn,"INSERT INTO users (login, password) VALUES ($1,$2)",parameter,2,sql_result);
        executeSQL(conn, "SELECT * FROM users WHERE login =$1 AND password = $2", parameter, 2,sql_result);

        type = SUCCESS;
        response_p->type = type;
        char serialized[BUFLEN] = "";
        serialize(sql_result,serialized);
        strcpy(response_p->response_message,serialized);
        printf("%s\n",response_p->response_message);
        return response_p;
    }

    if(strcmp(command,"/getImages")==0){
        printf("\ngetImages\n");
        char* par1 = strtok(dup,"@");
        const char* parameter[] = {par1};
        parameter[0] = par1;
        executeSQL(conn, "SELECT COUNT(*) FROM images WHERE user_id =$1", parameter, 1,sql_result);

        int howManyPics = atoi(sql_result);
        printf("%i\n",howManyPics);
        //char sql_result_images [BIGBUFLEN*howManyPics];
        executeSQL(conn, "SELECT * FROM images WHERE user_id =$1", parameter, 1,sql_result);


        if(strlen(sql_result) == 1){
            type = FAIL;
            response_p->type = type;
            response_p->response_message = "Not found";
            return response_p;
        }
        type = SUCCESS;
        response_p->type = type;
        char serialized[BUFLEN] = "";
        serialize(sql_result,serialized);
        strcpy(response_p->response_message,serialized);
        return response_p;
    }
    
    if(strcmp(command,"/deleteImage")==0){
        printf("\ndeleteImage\n");
        char* par1 = strtok(dup,"@");
        const char* parameter[] = {par1};
        parameter[0] = par1;

        executeSQL(conn, "SELECT * FROM images WHERE image_id = $1", parameter, 1,sql_result);
        if(strlen(sql_result) > 0){
            executeSQL(conn, "DELETE images WHERE image_id = $1", parameter, 1,sql_result);
        }
        else{
            type = FAIL;
            response_p->type = type;
            response_p->response_message = "Not found boject to delete.";
            return response_p;
        }
        
        type = SUCCESS;
        response_p->type = type;
        strcpy(response_p->response_message,"Item deleted successfully.");
        return response_p;
        
    }
    // if(strcmp(command,"/postImage")==0){
    //     printf("\npostImage\n");
    //     //char temp[BIGBUFLEN];
    //     strcat(temp,strtok(NULL,"@"));
    //     char recvbuf[BUFLEN];
    //     while (1)
    //     {
    //         int res = recv(fd, recvbuf, BUFLEN, 0);
    //         if (res > 0)
    //         {
    //             strcat(temp,recvbuf);
    //             if (recvbuf[res-1]=='}') break;                
    //         }
    //     }
    //     char deserialized[BUFLEN] = "";
    //     deserialize(temp,deserialized);        
    //     char* tmp = strdup(deserialized);
    //     char* param1 = strtok(tmp, ",");
    //     char* param2 = strtok(NULL,",");
    //     char* param3 = strtok(NULL,",");
    //     char* param4 = strtok(NULL,",");
    //     char* param5 = strtok(NULL,",");

    //     const char* parameter[] = { param1, param2, param3,param4,param5};
    //     parameter[0] = param1;
    //     parameter[1] = param2;
    //     parameter[2] = param3;
    //     parameter[3] = param4;
    //     parameter[4] = param5;

    //     executeSQL(conn, "INSERT INTO homework (image_bytes, image_date_added, user_id, image_filename, image_fileextension) VALUES ($1,$2,$3,$4,$5)", parameter, 5,sql_result);

    //     if(strcmp(sql_result,"-1") == 0){
    //         type = FAIL;
    //         response_p->type = type;
    //         response_p->response_message = "Deletion failed.";
    //         return response_p;
    //     }

    //     type = SUCCESS;
    //     response_p->type = type;
    //     strcpy(response_p->response_message,"Item added successfully.");
    //     return response_p;
    // }

    printf("BadRequest\n");
    type = FAIL;
    response_p->type = type;
    response_p->response_message = "Bad request.";
    return response_p;
}

int processSession(int fd)
{
    char recvbuf[BUFLEN];
    int blockeratorLength = strlen(blockerator); 
    int res = recv(fd, recvbuf, BUFLEN, 0);
        
    //Проверка секретного слова
    if (res > 0)
    {
        recvbuf[res] = '\0';
        if(strcmp(recvbuf,key)!=0){
            send(fd, blockerator, blockeratorLength, 0);
            close(fd);
            return 0;
        }
    }
    else{
        send(fd, blockerator, blockeratorLength, 0);
        close(fd);
        return 0;
    }

    //Приветствие
    int welcomeLength = strlen(welcome); 
    send(fd, welcome, welcomeLength, 0);

    //Подключение к БД
    conn = 0;
    if (conn == 0) {
        conn = PQconnectdb(conninfo);
        if (PQstatus(conn) != CONNECTION_OK) {
            fprintf(stderr, "Connection to database failed: %s\n", PQerrorMessage(conn));
            PQfinish(conn);
            return 0;
        }
        fprintf(stderr, "Connection to db\n");
    }
     
    int main_run = 1;
    while (main_run)
    {
        int res = recv(fd, recvbuf, BUFLEN, 0);
        
        if (res > 0)
        {
            recvbuf[res] = '\0';
            printf("Received (%d): %s\n", res, recvbuf);
            char* command = strtok(recvbuf,"|");
            printf("\n----------------\nCommand: %s\n",command);

            char* request_string = strtok(NULL,"|");

            printf("Request: %s\n",request_string);
            struct Response* response = processRequest(conn, fd, command, request_string);

            if(strcmp(response->response_message, "Goodbye.") == 0){
                processResponse(fd,response->type,response->response_message);
                printf("disconected\n");
                close(fd);
                PQfinish(conn);
                break;
            }
            processResponse(fd,response->type,response->response_message);
        }
        else
        {
            printf("disconected\n");
            close(fd);
            PQfinish(conn);
            break;
        }
    }
    return 0;
}

int main() 
{
    struct sockaddr_in addr;

    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(PORT);
    addr.sin_addr.s_addr = INADDR_ANY;

    if ((sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0) 
    {
        fprintf(stderr, "Unable to create socket\n");
        return 1;
    }

    if (bind(sockfd, (struct sockaddr *) &addr, sizeof(addr)) < 0) 
    {
        fprintf(stderr, "Unable to bind socket\n");
        close(sockfd);
        return 1;
    }

    if (listen(sockfd, 6) < 0) 
    {
        fprintf(stderr, "Unable to listen socket\n");
        close(sockfd);
        return 1;
    }

    fprintf(stdout, "Server started. IP:port - 82.179.140.18:%i. Waiting client connection...\n", PORT);    

    signal(SIGINT, sigint_handler);    

    while (1) 
    {
        struct sockaddr *ca = NULL;
        socklen_t sz = 0;
        int fd = accept(sockfd, ca, &sz);

        if (fd < 0) 
        {
            fprintf(stderr, "Unable to listen socket\n");
            sleep(2);
            continue;
        }

        pid_t pid = fork();        

        if (pid < 0) 
        {
            fprintf(stderr, "Unable to fork process\n");
            close(fd);
            return -1;
        }

        if (pid == 0) 
        {
            fprintf(stderr, "New session started\n");
            close (sockfd);
            processSession(fd);   
            printf("Goodbye\n");         
            return 0;
        }
    }
    printf("Goodbye\n");
    return 0;
}